package com.eafc26.discordstats.ea.mapping

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EaPositionCodeDecoderTest {
    @Test fun `external candidates remain explicitly unverified`() {
        assertThat(EaPositionCodeDecoder.decode("0")).isEqualTo(EaPositionCodeDecoder.DecodedPosition("0", "GK", "KNOWN_EXTERNAL_CANDIDATE", "UNVERIFIED_EXTERNAL_MAPPING"))
        assertThat(EaPositionCodeDecoder.decode("5").candidateLabel).isEqualTo("CB")
        assertThat(EaPositionCodeDecoder.decode("14").candidateLabel).isEqualTo("CM")
        assertThat(EaPositionCodeDecoder.decode("25").candidateLabel).isEqualTo("ST")
    }

    @Test fun `unknown malformed and missing codes never receive a fabricated label`() {
        assertThat(EaPositionCodeDecoder.decode("99").classification).isEqualTo("UNKNOWN_CODE")
        assertThat(EaPositionCodeDecoder.decode("abc").classification).isEqualTo("MALFORMED")
        assertThat(EaPositionCodeDecoder.decode(null).classification).isEqualTo("MISSING")
        assertThat(EaPositionCodeDecoder.decode(null).rawCode).isNull()
        assertThat(EaPositionCodeDecoder.decode("0").rawCode).isEqualTo("0")
    }
}
