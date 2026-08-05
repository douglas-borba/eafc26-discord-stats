import AppKit
import SwiftUI

// MARK: - Backend Process Registry (for signal handler)

nonisolated(unsafe) var _backendPID: pid_t = 0

private func installSignalHandlers() {
    let handler: @convention(c) (Int32) -> Void = { _ in
        let pid = _backendPID
        if pid > 0 {
            kill(pid, SIGTERM)
            var waited: Int32 = 0
            for _ in 0..<30 {
                var status: Int32 = 0
                let r = waitpid(pid, &status, WNOHANG)
                if r == pid || r == -1 { waited = 1; break }
                usleep(500_000)
            }
            if waited == 0 { kill(pid, SIGKILL) }
        }
        _exit(0)
    }
    signal(SIGTERM, handler)
    signal(SIGINT, handler)
    signal(SIGHUP, handler)
}

// MARK: - App Entry

@main
struct EAFCStatsLauncherApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            LauncherView()
                .environmentObject(appDelegate.launcher)
                .frame(width: 420, height: 340)
        }
        .windowStyle(.hiddenTitleBar)
        .windowResizability(.contentSize)
    }
}

// MARK: - App Delegate

@MainActor
class AppDelegate: NSObject, NSApplicationDelegate {
    let launcher = LauncherController()

    func applicationDidFinishLaunching(_ notification: Notification) {
        installSignalHandlers()
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)

        if let window = NSApp.windows.first {
            window.center()
            window.makeKeyAndOrderFront(nil)
            window.isMovableByWindowBackground = true
        }

        Task { await launcher.start() }
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ a: NSApplication) -> Bool { false }

    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        if launcher.isShuttingDown { return .terminateNow }

        Task {
            await launcher.shutdownApplication(source: "system")
        }
        return .terminateCancel
    }
}

// MARK: - Launcher State

enum LauncherPhase: Equatable {
    case starting
    case locatingProject
    case validatingConfig
    case startingBackend
    case waitingForReadiness(elapsed: Int)
    case openingDashboard
    case ready
    case existingInstanceFound
    case shuttingDown
    case error(title: String, detail: String, canRetry: Bool, canChooseDir: Bool)
}

// MARK: - Launcher Controller

@MainActor
class LauncherController: ObservableObject {
    @Published var phase: LauncherPhase = .starting
    @Published var statusMessage: String = "Preparando EA FC STATS…"
    @Published var detailMessage: String = ""
    @Published private(set) var isShuttingDown = false

    private var backendProcess: Process?
    private var backendStartedByUs = false
    private var projectDir: String?
    private let port = 8080
    private let timeoutSeconds = 120
    private let shutdownTimeoutSeconds = 15

    private var startupTask: Task<Void, Never>?
    private var cancelled = false

    private let appSupportDir: String = {
        let home = FileManager.default.homeDirectoryForCurrentUser.path
        return "\(home)/Library/Application Support/EAFC26DiscordStats"
    }()

    private var projectPathFile: String { "\(appSupportDir)/project-path" }

    private var logDir: String {
        let home = FileManager.default.homeDirectoryForCurrentUser.path
        return "\(home)/Library/Logs/EAFC26DiscordStats"
    }
    private var launcherLogFile: String { "\(logDir)/launcher.log" }
    private var backendLogFile: String { "\(logDir)/backend.log" }

    func start() async {
        cancelled = false

        do {
            try FileManager.default.createDirectory(atPath: appSupportDir, withIntermediateDirectories: true)
            try FileManager.default.createDirectory(atPath: logDir, withIntermediateDirectories: true)
        } catch {
            showError("Erro ao criar diretórios", detail: error.localizedDescription)
            return
        }

        log("Launcher started")

        if cancelled { return }

        if await isBackendHealthy() {
            phase = .existingInstanceFound
            statusMessage = "EA FC STATS já está em execução"
            detailMessage = "Uma instância existente foi encontrada na porta \(port)."
            log("Existing instance found on port \(port)")
            openDashboard()
            return
        }

        if cancelled { return }

        phase = .locatingProject
        statusMessage = "Localizando projeto…"
        guard let dir = await resolveProjectDir() else { return }
        if cancelled { return }
        projectDir = dir
        log("Project directory: \(dir)")

        phase = .validatingConfig
        statusMessage = "Verificando configuração…"
        detailMessage = ""

        guard validatePrerequisites(dir) else { return }
        if cancelled { return }

        let jarPath = findBootJar(in: dir)
        guard let jar = jarPath else {
            showError(
                "JAR não encontrado",
                detail: "O arquivo executável do backend não foi encontrado.\n\nExecute no Terminal:\n\ncd \"\(dir)\"\n./gradlew bootJar\n\nDepois abra o EA FC STATS novamente.",
                canRetry: true
            )
            return
        }
        log("Using JAR: \(jar)")

        let envVars = loadEnvLocal(at: "\(dir)/.env.local")

        if cancelled { return }

        phase = .startingBackend
        statusMessage = "Iniciando backend…"
        detailMessage = "Isso pode levar alguns segundos."

        guard startBackend(jar: jar, projectDir: dir, env: envVars) else { return }

        if cancelled { return }

        let ready = await waitForReadiness()
        guard ready else { return }

        if cancelled { return }

        phase = .openingDashboard
        statusMessage = "Abrindo dashboard…"
        detailMessage = ""

        openDashboard()

        phase = .ready
        statusMessage = "EA FC STATS pronto"
        detailMessage = "O dashboard foi aberto no navegador."
        log("Dashboard opened — launcher standing by")
    }

    func retry() {
        guard !isShuttingDown else { return }
        phase = .starting
        statusMessage = "Preparando EA FC STATS…"
        detailMessage = ""
        startupTask?.cancel()
        startupTask = Task { await start() }
    }

    func chooseNewDirectory() {
        guard !isShuttingDown else { return }
        try? FileManager.default.removeItem(atPath: projectPathFile)
        retry()
    }

    func openDashboard() {
        guard !isShuttingDown else { return }
        let url = URL(string: "http://localhost:\(port)")!
        NSWorkspace.shared.open(url)
    }

    func openLogs() {
        NSWorkspace.shared.selectFile(launcherLogFile, inFileViewerRootedAtPath: logDir)
    }

    func restartBackend() {
        guard !isShuttingDown else { return }
        Task {
            await performShutdownBackend(source: "restart")
            await waitForPortRelease()
            phase = .starting
            statusMessage = "Preparando EA FC STATS…"
            detailMessage = ""
            await start()
        }
    }

    func shutdownApplication(source: String = "button") async {
        guard !isShuttingDown else { return }
        isShuttingDown = true
        cancelled = true

        startupTask?.cancel()
        startupTask = nil

        log("Shutdown requested (source: \(source))")

        if backendStartedByUs {
            phase = .shuttingDown
            statusMessage = "Encerrando EA FC STATS…"
            detailMessage = ""

            await performShutdownBackend(source: source)
        } else {
            log("Backend not owned by launcher — skipping backend shutdown")
        }

        log("Launcher terminated")
        NSApp.terminate(nil)
    }

    // MARK: - Backend Shutdown

    private func performShutdownBackend(source: String) async {
        guard let process = backendProcess, process.isRunning else {
            log("No running backend process to shut down")
            backendProcess = nil
            backendStartedByUs = false
            return
        }

        let pid = process.processIdentifier
        log("Sending SIGTERM to backend (PID \(pid))")
        process.terminate()

        let terminated = await waitForProcessExit(process: process, timeout: shutdownTimeoutSeconds)

        if !terminated {
            log("Backend did not exit after \(shutdownTimeoutSeconds)s — sending SIGKILL (PID \(pid))")
            kill(pid, SIGKILL)
            _ = await waitForProcessExit(process: process, timeout: 3)
        }

        log("Backend process terminated (PID \(pid))")
        _backendPID = 0
        backendProcess = nil
        backendStartedByUs = false
    }

    private func waitForProcessExit(process: Process, timeout: Int) async -> Bool {
        let deadline = Date().addingTimeInterval(Double(timeout))
        while process.isRunning && Date() < deadline {
            try? await Task.sleep(nanoseconds: 200_000_000)
        }
        return !process.isRunning
    }

    private func waitForPortRelease() async {
        var attempts = 0
        while attempts < 15 {
            if await !isBackendHealthy() { return }
            try? await Task.sleep(nanoseconds: 500_000_000)
            attempts += 1
        }
        log("Port \(port) not released after waiting")
    }

    // MARK: - Private

    private func resolveProjectDir() async -> String? {
        if let saved = try? String(contentsOfFile: projectPathFile, encoding: .utf8) {
            let trimmed = saved.trimmingCharacters(in: .whitespacesAndNewlines)
            if isValidProject(trimmed) {
                return trimmed
            }
            log("Saved project path is stale: \(trimmed)")
            try? FileManager.default.removeItem(atPath: projectPathFile)
        }

        guard let chosen = await pickFolder() else {
            showError("Seleção cancelada", detail: "Nenhuma pasta foi selecionada.", canRetry: true, canChooseDir: true)
            return nil
        }

        let cleaned = chosen.hasSuffix("/") ? String(chosen.dropLast()) : chosen

        guard isValidProject(cleaned) else {
            showError(
                "Diretório inválido",
                detail: "A pasta selecionada não contém o projeto EA FC STATS.\n\nEsperado: build.gradle.kts e o JAR do backend.",
                canRetry: true,
                canChooseDir: true
            )
            return nil
        }

        try? cleaned.write(toFile: projectPathFile, atomically: true, encoding: .utf8)
        return cleaned
    }

    private func pickFolder() async -> String? {
        return await withCheckedContinuation { continuation in
            DispatchQueue.main.async {
                let panel = NSOpenPanel()
                panel.title = "Selecione a pasta do projeto EA FC STATS"
                panel.canChooseFiles = false
                panel.canChooseDirectories = true
                panel.allowsMultipleSelection = false
                panel.canCreateDirectories = false

                let result = panel.runModal()
                if result == .OK, let url = panel.url {
                    continuation.resume(returning: url.path)
                } else {
                    continuation.resume(returning: nil)
                }
            }
        }
    }

    private func isValidProject(_ dir: String) -> Bool {
        FileManager.default.fileExists(atPath: "\(dir)/build.gradle.kts")
    }

    private func findBootJar(in dir: String) -> String? {
        let libsDir = "\(dir)/build/libs"
        guard let files = try? FileManager.default.contentsOfDirectory(atPath: libsDir) else { return nil }
        if let jar = files.first(where: { $0.hasSuffix(".jar") && !$0.contains("-plain") }) {
            return "\(libsDir)/\(jar)"
        }
        return nil
    }

    private func validatePrerequisites(_ dir: String) -> Bool {
        let envFile = "\(dir)/.env.local"
        if !FileManager.default.fileExists(atPath: envFile) {
            showError(
                "Configuração ausente",
                detail: "Arquivo .env.local não encontrado em:\n\(dir)\n\nCrie o arquivo com as variáveis de ambiente necessárias.",
                canRetry: true,
                canChooseDir: true
            )
            return false
        }

        if findJava() == nil {
            showError(
                "Java não encontrado",
                detail: "O JDK 21 ou superior é necessário para executar o backend.\n\nInstale via:\nbrew install openjdk@21",
                canRetry: true
            )
            return false
        }

        return true
    }

    private func findJava() -> String? {
        let candidates = [
            "/usr/bin/java",
            "/usr/local/bin/java",
            "/opt/homebrew/bin/java",
        ]
        for path in candidates {
            if FileManager.default.isExecutableFile(atPath: path) {
                return path
            }
        }
        let whichProcess = Process()
        whichProcess.executableURL = URL(fileURLWithPath: "/usr/bin/which")
        whichProcess.arguments = ["java"]
        let pipe = Pipe()
        whichProcess.standardOutput = pipe
        whichProcess.standardError = FileHandle.nullDevice
        try? whichProcess.run()
        whichProcess.waitUntilExit()
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        let result = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines)
        return (result?.isEmpty == false) ? result : nil
    }

    private func loadEnvLocal(at path: String) -> [String: String] {
        var env: [String: String] = [:]
        guard let content = try? String(contentsOfFile: path, encoding: .utf8) else { return env }

        for line in content.components(separatedBy: .newlines) {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty || trimmed.hasPrefix("#") { continue }
            let commentStripped = trimmed.components(separatedBy: " #").first ?? trimmed
            guard let eqIdx = commentStripped.firstIndex(of: "=") else { continue }
            let key = String(commentStripped[commentStripped.startIndex..<eqIdx])
                .trimmingCharacters(in: .whitespaces)
                .replacingOccurrences(of: "export ", with: "")
            var value = String(commentStripped[commentStripped.index(after: eqIdx)...])
                .trimmingCharacters(in: .whitespaces)
            if (value.hasPrefix("\"") && value.hasSuffix("\"")) ||
               (value.hasPrefix("'") && value.hasSuffix("'")) {
                value = String(value.dropFirst().dropLast())
            }
            if !key.isEmpty {
                env[key] = value
            }
        }
        return env
    }

    private func startBackend(jar: String, projectDir: String, env: [String: String]) -> Bool {
        guard let javaPath = findJava() else {
            showError("Java não encontrado", detail: "Não foi possível localizar o Java.")
            return false
        }

        let process = Process()
        process.executableURL = URL(fileURLWithPath: javaPath)
        process.arguments = [
            "-Deafc.dashboard.auto-open=false",
            "-jar", jar,
        ]
        process.currentDirectoryURL = URL(fileURLWithPath: projectDir)

        var processEnv = ProcessInfo.processInfo.environment
        for (k, v) in env { processEnv[k] = v }
        process.environment = processEnv

        let logFile = FileHandle.forWritingOrCreate(atPath: backendLogFile)
        process.standardOutput = logFile
        process.standardError = logFile

        do {
            try process.run()
            backendProcess = process
            backendStartedByUs = true
            _backendPID = process.processIdentifier
            log("Backend started (PID \(process.processIdentifier))")
            return true
        } catch {
            showError("Falha ao iniciar backend", detail: error.localizedDescription)
            return false
        }
    }

    private func waitForReadiness() async -> Bool {
        var elapsed = 0
        while elapsed < timeoutSeconds {
            if cancelled { return false }

            if let process = backendProcess, !process.isRunning {
                showError(
                    "Backend encerrou inesperadamente",
                    detail: "O processo do backend foi finalizado antes de ficar disponível.\n\nVerifique os logs para mais detalhes.",
                    canRetry: true
                )
                return false
            }

            phase = .waitingForReadiness(elapsed: elapsed)
            statusMessage = "Aguardando o sistema ficar disponível…"
            detailMessage = elapsed > 0 ? "\(elapsed)s" : ""

            if await isBackendHealthy() {
                return true
            }

            try? await Task.sleep(nanoseconds: 2_000_000_000)
            elapsed += 2
        }

        showError(
            "Timeout de inicialização",
            detail: "O backend não ficou disponível em \(timeoutSeconds) segundos.\n\nVerifique os logs para mais detalhes.",
            canRetry: true
        )
        return false
    }

    private func isBackendHealthy() async -> Bool {
        let url = URL(string: "http://localhost:\(port)/api/health")!
        var request = URLRequest(url: url)
        request.timeoutInterval = 2
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) {
                return true
            }
        } catch {}
        return false
    }

    private func showError(_ title: String, detail: String, canRetry: Bool = false, canChooseDir: Bool = false) {
        guard !isShuttingDown else { return }
        phase = .error(title: title, detail: detail, canRetry: canRetry, canChooseDir: canChooseDir)
        statusMessage = title
        detailMessage = detail
        log("ERROR: \(title) — \(detail)")
    }

    private func log(_ message: String) {
        let timestamp = ISO8601DateFormatter().string(from: Date())
        let line = "[\(timestamp)] \(message)\n"
        if let data = line.data(using: .utf8) {
            let handle = FileHandle.forWritingOrCreate(atPath: launcherLogFile)
            handle.seekToEndOfFile()
            handle.write(data)
            handle.closeFile()
        }
    }
}

// MARK: - FileHandle Extension

extension FileHandle {
    static func forWritingOrCreate(atPath path: String) -> FileHandle {
        if !FileManager.default.fileExists(atPath: path) {
            FileManager.default.createFile(atPath: path, contents: nil)
        }
        return FileHandle(forWritingAtPath: path) ?? FileHandle.nullDevice
    }
}

// MARK: - SwiftUI Views

struct LauncherView: View {
    @EnvironmentObject var launcher: LauncherController

    var body: some View {
        VStack(spacing: 0) {
            headerView
            Divider()
            contentView
        }
        .background(.background)
    }

    private var headerView: some View {
        VStack(spacing: 6) {
            Image(systemName: "sportscourt.fill")
                .font(.system(size: 40))
                .foregroundStyle(.blue)
            Text("EA FC STATS")
                .font(.system(size: 20, weight: .bold, design: .rounded))
        }
        .padding(.top, 28)
        .padding(.bottom, 16)
    }

    @ViewBuilder
    private var contentView: some View {
        switch launcher.phase {
        case .ready:
            readyView
        case .existingInstanceFound:
            existingInstanceView
        case .shuttingDown:
            shuttingDownView
        case .error(let title, let detail, let canRetry, let canChooseDir):
            errorView(title: title, detail: detail, canRetry: canRetry, canChooseDir: canChooseDir)
        default:
            loadingView
        }
    }

    private var loadingView: some View {
        VStack(spacing: 12) {
            ProgressView()
                .controlSize(.regular)
            Text(launcher.statusMessage)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(.primary)
            if !launcher.detailMessage.isEmpty {
                Text(launcher.detailMessage)
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button("Cancelar") {
                Task { await launcher.shutdownApplication(source: "cancel-button") }
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
            .disabled(launcher.isShuttingDown)
        }
        .padding(.top, 24)
        .padding(.bottom, 16)
    }

    private var readyView: some View {
        VStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 28))
                .foregroundStyle(.green)
            Text("EA FC STATS em execução")
                .font(.system(size: 13, weight: .medium))

            Spacer()

            VStack(spacing: 8) {
                Button("Abrir Dashboard") { launcher.openDashboard() }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.regular)
                    .disabled(launcher.isShuttingDown)

                HStack(spacing: 12) {
                    Button("Ver Logs") { launcher.openLogs() }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                    Button("Reiniciar") { launcher.restartBackend() }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                        .disabled(launcher.isShuttingDown)
                    Button("Encerrar") {
                        Task { await launcher.shutdownApplication(source: "quit-button") }
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                    .disabled(launcher.isShuttingDown)
                }
            }
        }
        .padding(.top, 20)
        .padding(.bottom, 16)
    }

    private var existingInstanceView: some View {
        VStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 28))
                .foregroundStyle(.green)
            Text("Instância existente encontrada")
                .font(.system(size: 13, weight: .medium))
            Text("O dashboard já foi aberto no navegador.")
                .font(.system(size: 11))
                .foregroundStyle(.secondary)

            Spacer()

            HStack(spacing: 12) {
                Button("Abrir Dashboard") { launcher.openDashboard() }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.regular)
                Button("Fechar") {
                    Task { await launcher.shutdownApplication(source: "close-button") }
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
        }
        .padding(.top, 20)
        .padding(.bottom, 16)
    }

    private var shuttingDownView: some View {
        VStack(spacing: 12) {
            ProgressView()
                .controlSize(.regular)
            Text("Encerrando EA FC STATS…")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(.primary)
            Spacer()
        }
        .padding(.top, 24)
        .padding(.bottom, 16)
    }

    private func errorView(title: String, detail: String, canRetry: Bool, canChooseDir: Bool) -> some View {
        VStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 28))
                .foregroundStyle(.orange)
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .multilineTextAlignment(.center)
            Text(detail)
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .lineLimit(6)

            Spacer()

            VStack(spacing: 8) {
                HStack(spacing: 12) {
                    if canRetry {
                        Button("Tentar Novamente") { launcher.retry() }
                            .buttonStyle(.borderedProminent)
                            .controlSize(.regular)
                            .disabled(launcher.isShuttingDown)
                    }
                    if canChooseDir {
                        Button("Escolher Pasta…") { launcher.chooseNewDirectory() }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                            .disabled(launcher.isShuttingDown)
                    }
                }
                HStack(spacing: 12) {
                    Button("Ver Logs") { launcher.openLogs() }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                    Button("Fechar") {
                        Task { await launcher.shutdownApplication(source: "close-button") }
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                }
            }
        }
        .padding(.top, 20)
        .padding(.bottom, 16)
        .padding(.horizontal, 20)
    }
}
