package com.eafc26.discordstats.web

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class NotifyResponse(val status: String, val message: String, val summary: String? = null)
