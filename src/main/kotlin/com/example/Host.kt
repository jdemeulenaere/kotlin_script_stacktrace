package com.example

import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.system.exitProcess

@KotlinScript(fileExtension = "test.kts")
abstract class TestScript {
  internal val testBuilders = arrayListOf<TestBuilder.() -> Unit>()

  fun test(builder: TestBuilder.() -> Unit) {
    testBuilders.add(builder)
  }
}

interface TestBuilder {
  fun doSomething()
}

fun main(args: Array<String>) {
  if (args.size != 1) {
    println("Usage: program /path/to/script")
    exitProcess(1)
  }

  val file = File(args[0])
  Host().evaluate(file)
}

class Host {
  private val jvmHost = BasicJvmScriptingHost()
  private val compilationConfiguration = createCompilationConfigurationFromTemplate(
    KotlinType(TestScript::class),
    defaultJvmScriptingHostConfiguration
  ) {
    jvm {
      dependenciesFromClassContext(TestScript::class, wholeClasspath = true)
    }
  }

  fun evaluate(file: File) {
    val scriptSource = file.toScriptSource()
    val result = jvmHost.eval(scriptSource, compilationConfiguration, null)

    if (result is ResultWithDiagnostics.Failure) {
      println("Script evaluation failed:")
      result.reports.forEach { report ->
        println(" - [${report.severity}] ${report.message}")
      }
      return
    }

    val scriptInstance = (result as ResultWithDiagnostics.Success).value.returnValue.scriptInstance
    val testScript = scriptInstance as? TestScript ?: return
    val testBuilders = testScript.testBuilders

    println("Script has ${testBuilders.size} tests.\n")
    testBuilders.forEachIndexed { index, builder ->
      println("=== Test n°${index + 1}===\n")

      // Apply the test builder on an implement that just prints the stack trace when doSomething()
      // is called.
      object : TestBuilder {
        override fun doSomething() {
          println("doSomething() was called with the following stacktrace:")
          Thread.currentThread().stackTrace.forEach { println("\t$it") }
          println()
        }
      }.apply(builder)
    }
  }
}