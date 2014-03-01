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

package coop.plausible.scala.nx

import scala.tools.nsc.{Phase, Global}
import scala.tools.nsc.plugins.{PluginComponent, Plugin}

/**
 * No Exceptions Plugin
 * @param global Compiler state.
 */
class NXPlugin (val global: Global) extends Plugin with PluginCore {
  import global._

  override val name: String = "nx"
  override val description: String = "Checked exceptions for Scala. If you're stuck using exceptions, insist on Checked Brand Exceptions™."
  override val components: List[PluginComponent] = List(Component)

  private object Component extends PluginComponent {
    override def newPhase (prev: Phase )= new ValidationPhase(prev)

    override val runsAfter: List[String] = List("refchecks", "typer")
    override val phaseName: String = NXPlugin.this.name
    override val global: NXPlugin.this.global.type = NXPlugin.this.global

    /**
     * Exception validation phase.
     * @param prev The previous phase.
     */
    class ValidationPhase (prev: Phase) extends StdPhase(prev) {
      override def apply (unit: CompilationUnit) = {
        new ExceptionTraversal()(unit.body)
      }
    }
  }

}

