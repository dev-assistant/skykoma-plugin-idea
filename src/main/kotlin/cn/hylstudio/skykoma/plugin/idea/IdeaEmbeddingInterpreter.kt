package cn.hylstudio.skykoma.plugin.idea


import org.jetbrains.kotlinx.jupyter.repl.EvalResultEx
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdeaEmbeddingInterpreter {
//    private var compiler: KotlinReplWrapper = KotlinReplWrapper()
//    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)
//
//    fun eval(req: IdeaCodeRequest): Message {
//        try {
//            val result = compiler.eval(req.content, req.id)
//            return convertResult(result, req.id)
//        } catch (e: Exception) {
//            val content = ErrorContent(e.javaClass.name, e.toString())
//            return Message(req.id, "", MessageType.ERROR, content = content)
//        }
//    }
//
//    private fun convertResult(result: EvalResultEx, id: Int): Message {
//        val resultValue = result.rawValue
//        val className: String = resultValue?.javaClass?.name.orEmpty()
//
//        val message = Message(
//            id,
//            resultValue.toString(),
//        )
//
//        when (resultValue) {
////            is ReactiveAction -> {
////                message.action = resultValue
////            }
//        }
//
////        if (className.startsWith("org.archguard.dsl.design")) {
////            message.msgType = MessageType.ARCHGUARD_GRAPH
////        }
//
//        return message
//    }
}