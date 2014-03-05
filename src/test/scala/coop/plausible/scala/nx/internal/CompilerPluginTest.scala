/*
 * Copyright (c) 2014 Plausible Labs Cooperative, Inc.
 * All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package coop.plausible.scala.nx.internal

import org.specs2.mutable.Specification
import scala.tools.nsc.interpreter.{Results, IMain}
import org.specs2.main.CommandLineArguments

/**
 * Test compiler plugin-based execution. This assumes that the build environment (sbt by default) is configured to
 * execute our tests with the plugin enabled (eg, via scalacOptions).
 *
 * This is a basic smoke test for the plugin; the actual core implementation is tested in NXTest.
 */
class CompilerPluginTest extends Specification with CommandLineArguments {
  /**
   * The path to the NX plugin jar. This must be supplied via sbt, eg:
   * testOptions <+= (packageBin in Compile) map { p =>
   *   Tests.Argument("nx-plugin-path", p.toString)
   * }
   */
  private val pluginJar = arguments.commandLine.value("nx-plugin-path").getOrElse {
    throw new IllegalArgumentException("Missing nx-plugin-path test argument")
  }

  /**
   * Create and return a new interpreter
   */
  private def interpreter (args: String*): IMain = {
    /* Configure the compiler */
    val settings = new scala.tools.nsc.Settings
    settings.processArguments(args.toList, true)

    /* Inherit our Java classpath */
    settings.usejavacp.value = true

    /* Enable loading of our plugin */
    settings.plugin.appendToValue(pluginJar)

    /* Set up the interpreter */
    new IMain(settings)
  }

  /**
   * Instantiate a new interpreter with `args`, and then interpret `statement` silently, returning
   * the result.
   *
   * @param statement The statement to interpret.
   * @param args Compiler arguments.
   */
  private def interpret (statement: String, args: String*): Results.Result = {
    val i = interpreter(args:_*)
    i.beSilentDuring(i.interpret(statement))
  }

  "CompilerPlugin" should {
    "run as a compiler plugin" in {
      /* Run our smoke test */
      val interp = interpreter()
      interp.beSilentDuring {
        interp.interpret(
          """
            |def foo (value: Int): Int = value + 1942
            |val ret = foo(5)
          """.stripMargin)
      }
      interp.valueOfTerm("ret") must beSome(1947)
    }

    "run with standard checked exceptions by default" in {
      /* `RuntimeException` should be unchecked */
      interpret("def thrower (): Unit = throw new RuntimeException()") must beLike {
        case Results.Success => ok
        case _ => ko("Success")
      }

      /* `Exception` should be checked */
      interpret("def thrower (): Unit = throw new Exception()") must beLike {
        case Results.Error => ok
        case _ => ko("Error")
      }
    }

    "support checked:standard argument" in {
      val arg = "-P:nx:checked:standard"

      /* `RuntimeException` should be unchecked */
      interpret("def thrower (): Unit = throw new RuntimeException()", arg) must beLike {
        case Results.Success => ok
        case _ => ko("Success")
      }

      /* `Exception` should be checked */
      interpret("def thrower (): Unit = throw new Exception()", arg) must beLike {
        case Results.Error => ok
        case _ => ko("Error")
      }
    }

    "support checked:strict argument" in {
      val arg = "-P:nx:checked:strict"

      /* `RuntimeException` should be checked */
      interpret("def thrower (): Unit = throw new Exception()", arg) must beLike {
        case Results.Error => ok
        case _ => ko("Error")
      }

      /* `Error` should be unchecked */
      interpret("def thrower (): Unit = throw new Error()", arg) must beLike {
        case Results.Success => ok
        case _ => ko("Success")
      }
    }

    "support checked:fatal argument" in {
      val arg = "-P:nx:checked:fatal"

      /* `RuntimeException` should be checked */
      interpret("def thrower (): Unit = throw new Exception()", arg) must beLike {
        case Results.Error => ok
        case _ => ko("Error")
      }

      /* `Error` should be checked */
      interpret("def thrower (): Unit = throw new Error()", arg) must beLike {
        case Results.Error => ok
        case _ => ko("Error")
      }

      /* `OutOfMemory` should be unchecked */
      interpret("def thrower (): Unit = throw new OutOfMemoryError()", arg) must beLike {
        case Results.Success => ok
        case _ => ko("Success")
      }
    }
  }
}
