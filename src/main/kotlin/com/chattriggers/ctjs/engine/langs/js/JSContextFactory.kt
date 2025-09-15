package com.chattriggers.ctjs.engine.langs.js

import org.mozilla.javascript.*
import org.mozilla.javascript.Context.FEATURE_LOCATION_INFORMATION_IN_ERROR
import java.net.URL
import java.net.URLClassLoader

object JSContextFactory : ContextFactory() {
    private var classLoader = ModifiedURLClassLoader()
    var optimize = true

    fun addAllURLs(urls: List<URL>) = classLoader.addAllURLs(urls)

    internal fun closeLoader() = classLoader.close()

    internal fun buildLoader() {
        classLoader = ModifiedURLClassLoader()
    }

    override fun onContextCreated(cx: Context) {
        super.onContextCreated(cx)

        cx.applicationClassLoader = classLoader
        cx.optimizationLevel = if (optimize) 9 else 0
        cx.languageVersion = Context.VERSION_ES6
        cx.errorReporter = JSErrorReporter(JSLoader.console.writer.printWriter)
        cx.wrapFactory = WrapFactory().apply { isJavaPrimitiveWrap = false }

        addListener(object : Listener {
            override fun contextCreated(ctx: Context) {
                ctx.languageVersion = 200
                ctx.setTrackUnhandledPromiseRejections(true)
            }

            override fun contextReleased(ctx: Context) {
                ctx.processMicrotasks()

                ctx.unhandledPromiseTracker.process { result: Any? ->
                    var msg = "Unhandled rejected promise: " + Context.toString(result)
                    if (result is Scriptable) {
                        val stack = ScriptableObject.getProperty(result as Scriptable?, "stack")
                        if (stack != null && stack !== Scriptable.NOT_FOUND) {
                            msg += """
                        
                        ${Context.toString(stack)}
                        """.trimIndent()
                        }
                    }
                    // TODO: perhaps make this be in console instead of logs
                    println(msg)
                }
            }
        })
    }

    override fun hasFeature(cx: Context?, featureIndex: Int): Boolean {
        if (featureIndex == FEATURE_LOCATION_INFORMATION_IN_ERROR) return true

        return super.hasFeature(cx, featureIndex)
    }

    private class ModifiedURLClassLoader : URLClassLoader(arrayOf(), javaClass.classLoader) {
        val sources = mutableListOf<URL>()

        fun addAllURLs(urls: List<URL>) {
            (urls - sources).forEach(::addURL)
        }

        public override fun addURL(url: URL) {
            super.addURL(url)
            sources.add(url)
        }
    }
}
