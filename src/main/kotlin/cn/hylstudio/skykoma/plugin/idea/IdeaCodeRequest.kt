package cn.hylstudio.skykoma.plugin.idea

import kotlinx.serialization.Serializable

@Serializable
data class IdeaCodeRequest(
    var id: Int = -1,
    val content: String,
)
