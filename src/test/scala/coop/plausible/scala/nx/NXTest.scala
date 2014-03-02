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

import org.specs2.mutable.Specification
import java.io.IOException

/**
 * NX implementation tests.
 */
class NXTest extends Specification {
  /* We use the macro to simplify testing */
  import NX.nx

  "NX" should {
    "traverse throw" in {
      NX.check {
        if (false) throw new IOException("Fake IO Exception")
      } must beEqualTo(Set(classOf[IOException]))

    }

    /*
    "traverse defs" in {
      nx {
        def foo (value: Int) = value
        true
      } must beTrue
    }

    "traverse try-catch" in {
      nx {
        try {
          throw new IOException("Your jib, it's not cut right")
        } catch {
          case e:IOException => true
        }
      } must beTrue
    }

    "traverse constructors" in {
      nx {
        class TestClass @throws[IOException]("If the name strikes us as unfortunate") (name: String) {
          def this () = this("a name")
        }
        new TestClass()
        true
      } must beTrue

    }
    */
  }
}
