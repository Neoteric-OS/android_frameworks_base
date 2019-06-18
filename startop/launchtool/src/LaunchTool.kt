package com.android.startop.test.launchtool

import android.app.Instrumentation
import android.os.Bundle
import android.support.test.launcherhelper.LauncherStrategyFactory
import android.support.test.uiautomator.UiDevice

class LaunchToolInstrumentation : Instrumentation() {
    

    override fun onCreate(bundle: Bundle) {
        super.onCreate(bundle)
        start()
    }

    override fun onStart() {
        println("Beginning launchtool instrumentation")

        var app = "Gallery"
        println("launching " + app)

        val automation = getUiAutomation()
        println("got ui automation: " + automation)

        println("created instrumentation")
        val device = UiDevice.getInstance(this)
        println("created device")
        val strategy = LauncherStrategyFactory.getInstance(device).getLauncherStrategy()
        println("created strategy")
        strategy.launch(app, null)
        println("done")
    }
}

fun main(args : Array<String>) {
    try {
        realMain(args)
    } catch (e : Throwable) {
        e.printStackTrace(System.err)
    }
}

fun realMain(args : Array<String>) {
    if (args.size != 1) {
        println("usage: launchtool <app>")
        return;
    }

    val app = args[0];

    println("launching " + app)
    val instrumentation = Instrumentation()
    println("created instrumentation")
    val device = UiDevice.getInstance(instrumentation)
    println("created device")
    val strategy = LauncherStrategyFactory.getInstance(device).getLauncherStrategy()
    println("created strategy")
    strategy.launch(app, null)
    println("done")
}
