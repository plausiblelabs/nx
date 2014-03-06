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

package coop.plausible.nx.internal

import scala.tools.nsc.{Phase, Global}
import scala.tools.nsc.plugins.{PluginComponent, Plugin}

/**
 * Compiler plugin APIs.
 */
private[internal] object CompilerPlugin {
  /** Compiler plugin name */
  val name: String = "nx"

  /** Compiler option prefix when running from within a macro. */
  val macroTimeOptionPrefix = s"-P:$name:"

  /** Compiler option prefix when parsing options at compile time via Plugin.processOptions */
  val compileTimeOptionPrefix = ""

  /**
   * Parse the compiler command line options to fetch the configued checked exception strategy.
   *
   * @param nx The NX universe from which a checked exception strategy will be returned.
   * @param optionPrefix The prefix to filter from all options. For macros, this will be `macroTimeOptionPrefix`; in the compiler,
   *                     this will be `compileTimeOptionPrefix`
   * @param options A list of compiler options to be parsed.
   * @return An appropriate strategy, or None if unspecified. If None, StandardCheckedExceptionStrategy should be used.
   */
  def parseCheckedExceptionStrategy (nx: Validator, optionPrefix: String, options: List[String]): Option[nx.CheckedExceptionStrategy] = {
    /* Clean up the options */
    val nxOptions = options.filter(_.startsWith(optionPrefix)).map(_.substring(optionPrefix.length))

    /* Extract the last 'checked' options */
    val checkedPrefix = "checked:"
    val checkedOption = nxOptions.filter(_.startsWith(checkedPrefix)).map(_.substring(checkedPrefix.length)).lastOption

    /* Map to a checked strategy */
    checkedOption.flatMap {
      case "standard" => Some(nx.StandardCheckedExceptionStrategy)
      case "strict" => Some(nx.StrictCheckedExceptionStrategy)
      case "fatal" => Some(nx.FatalCheckedExceptionStrategy)
      case _ => None
    }
  }
}

/**
 * No Exceptions compiler plugin.
 *
 * @param global Compiler state.
 */
class CompilerPlugin (val global: Global) extends Plugin {
  import CompilerPlugin._
  import global._

  override val name: String = CompilerPlugin.name
  override val description: String = "Checked exceptions for Scala. If you're stuck using exceptions, insist on Checked Brand Exceptions™."
  override val components: List[PluginComponent] = List(Component)

  /**
   * The checked exception strategy to be used.
   */
  private var checkedExceptionStrategy: Component.CheckedExceptionStrategy = Component.StandardCheckedExceptionStrategy

  override def processOptions(options: List[String], error: String => Unit) {
    /* Parse our known plugin options */
    for (option <- options) {
      option match {
        case _ if option.startsWith("checked") =>
          /* Fetching this value is handled below; here, we just validate it */
          if (parseCheckedExceptionStrategy(Component, compileTimeOptionPrefix, List(option)).isEmpty)
            error("Unknown checked exception value: "+ option.substring("checked:".length))
        case _ =>
          error(s"Unknown option: $option")
      }
    }

    /* Set the checked exception strategy; validation of the options is done above. */
    parseCheckedExceptionStrategy(Component, compileTimeOptionPrefix, options).foreach(checkedExceptionStrategy = _)
  }

  override val optionsHelp: Option[String] = Some(Seq(
    "  -P:nx:checked:standard             As in Java, RuntimeException and Error subclasses will be unchecked.",
    "  -P:nx:checked:strict               Only subclasses of Error will be unchecked.",
    "  -P:nx:checked:fatal                Only JVM-fatal exceptions (such as AssertionError and OutOfMemory) will be unchecked."
  ).mkString(System.getProperty("line.separator")))

  /**
   * Compiler component that defines our NX compilation phase; hands the
   * compilation unit off to the actual NX implementation.
   */
  private object Component extends PluginComponent with Validator {
    override def newPhase (prev: Phase )= new ValidationPhase(prev)

    override val runsAfter: List[String] = List("refchecks", "typer")
    override val phaseName: String = CompilerPlugin.this.name
    override val global: CompilerPlugin.this.global.type = CompilerPlugin.this.global

    /* NX API */
    override val universe = global

    /**
     * Exception validation phase.
     * @param prev The previous phase.
     */
    class ValidationPhase (prev: Phase) extends StdPhase(prev) {
      override def apply (unit: CompilationUnit) = {
        /* Perform the validation */
        val validator = new ThrowableValidator(checkedExceptionStrategy)
        validator.check(unit.body).foreach { err =>
          unit.error(err.pos, err.message)
        }
      }
    }
  }

}

