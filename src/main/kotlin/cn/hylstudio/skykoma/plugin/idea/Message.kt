package cn.hylstudio.skykoma.plugin.idea

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    var id: Int = -1,
    var resultValue: String,
    var msgType: MessageType = MessageType.NONE,
    var content: MessageContent? = null,
//    var action: ReactiveAction? = null,
)
