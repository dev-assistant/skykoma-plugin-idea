package cn.hylstudio.skykoma.plugin.idea.action;

import com.intellij.ide.script.IdeScriptEngine;
import com.intellij.ide.script.IdeScriptEngineManager;
import com.intellij.ui.content.Content;

interface ScriptEngineProvider {
    IdeScriptEngine getEngine(IdeScriptEngineManager.EngineInfo engineInfo, Content content);
}
