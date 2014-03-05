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

import scala.tools.nsc.{Phase, Global}
import scala.tools.nsc.plugins.{PluginComponent, Plugin}
import coop.plausible.scala.nx.NX

/**
 * No Exceptions compiler plugin.
 *
 * @param global Compiler state.
 */
class CompilerPlugin (val global: Global) extends Plugin {
  import global._

  override val name: String = "nx"
  override val description: String = "Checked exceptions for Scala. If you're stuck using exceptions, insist on Checked Brand Exceptions™."
  override val components: List[PluginComponent] = List(Component)

  /**
   * The checked exception strategy to be used.
   */
  private var checkedExceptionStrategy: Component.CheckedExceptionStrategy = Component.StandardCheckedExceptionStrategy

  override def processOptions(options: List[String], error: String => Unit) {
    /*
     * Parse our known plugin options
     */
    for (option <- options) {
      option match {
        case "checked:standard" => checkedExceptionStrategy = Component.StandardCheckedExceptionStrategy
        case "checked:strict" => checkedExceptionStrategy = Component.StrictCheckedExceptionStrategy
        case "checked:fatal" => checkedExceptionStrategy = Component.FatalCheckedExceptionStrategy
        case _ if option.startsWith("checked") =>
          error("Unknown checked exception value: "+ option.substring("checked:".length))
        case _ =>
          error(s"Unknown option: $option")
      }
    }
  }

  override val optionsHelp: Option[String] = Some(Seq(
    "  -P:nx:checked:standard             As in Java, RuntimeException and Error subclasses will be unchecked.",
    "  -P:nx:checked:strict               Only subclasses of Error will be unchecked.",
    "  -P:nx:checked:fatal                Only JVM-fatal exceptions (such as AssertionError and OutOfMemory) will be unchecked."
  ).mkString(System.getProperty("line.separator")))

  /**
   * Compiler component that defines our Macro compilation phase; hands the
   * compilation unit off to the actual Macro implementation.
   */
  private object Component extends PluginComponent with NX {
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

