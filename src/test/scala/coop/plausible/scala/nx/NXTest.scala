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
  /* NOTE: The NX.check() macro used below will elide all of the code within it from
   * the final output. /None/ of code found within the macro blocks is actually run. */
  import NX.nx

  "NX" should {
    /* Test extraction of explicit `throw` statements */
    "find throw statements" in {
      /* Find nested throw statements */
      NX.check {
        class MyClass {
          def doSomething (flag: Boolean) {
            if (flag) throw new IOException("I done did something")
          }
        }

        class OtherClass (flag: Boolean) {
          if (flag) throw new RuntimeException("Ha ha! You thought you were safe!")
        }
      } must beEqualTo(Set(classOf[IOException], classOf[RuntimeException]))
    }

    /* Test extraction of @throws annotations from called methods that are declared to throw */
    "find throw annotations on referenced methods" in {
      /* New-style constructor */
      @throws[RuntimeException]("") def scalaThrower (): Unit = ()

      /* Old-style constructor */
      @throws(classOf[IOException]) def oldScalaThrower (): Unit = ()

      NX.check {
        /* (External Java) -- defined to throw an UnknownHostException */
        java.net.InetAddress.getByName("")

        /* (Scala) -- defined to throw a RuntimeException */
        scalaThrower()

        /* (Scala) -- defined to throw an IOException */
        oldScalaThrower()
      } must beEqualTo(Set(classOf[java.net.UnknownHostException], classOf[RuntimeException], classOf[IOException]))
    }

    "exclude throwies based on an enclosing def's @throws annotation" in {
      NX.check {
        /* Should exclude the throw below */
        @throws[IOException]("If I don't like you")
        def foo (flag:Boolean) = { if (!flag) throw new IOException("No such luck!") }
      } must beEqualTo(Set())
    }

    "exclude supertype throwies based on an enclosing def's @throws annotations" in {
      NX.check {
        /* Should match on the Exception subtype */
        @throws[Exception]("If I don't like you")
        def foo (flag:Boolean) = { if (!flag) throw new IOException("No such luck!") }
      } must beEqualTo(Set())
    }

    "propagate non-subtype throwies based on an enclosing def's @throws annotations" in {
      NX.check {
        /* Should NOT match on the thrown supertype */
        @throws[IOException]("If I don't like you")
        def foo (flag:Boolean) = { if (!flag) throw new Exception("No such luck!") }
      } must beEqualTo(Set(classOf[Exception]))
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
