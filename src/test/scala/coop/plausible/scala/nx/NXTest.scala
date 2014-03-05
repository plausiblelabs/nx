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
import java.net.{SocketException, InetAddress, UnknownHostException}
import scala.util.control.NonFatal
import coop.plausible.scala.nx.ValidationResult.{UnhandledThrowable, CannotOverride}

/**
 * NX implementation tests.
 */
class NXTest extends Specification {
  /**
   * Generate a Scala specification reference.
   *
   * @param section Scala specification section number.
   */
  private def specRef (section: String): String = {
    /** The Scala specification version used in references. If you add a new reference to the spec,
      * make sure to update this. */
    val specVersion = "Scala Spec 2.9, March 3, 2014"

    s"$specVersion, Section $section"
  }

  /*
   * NOTES:
   * - The NX.check() macro used below will elide all of the code within it from
   *   the final output. /None/ of code found within the macro blocks is actually run.
   * - We use bool flags in the test code to avoid 'unreachable code' warnings triggered by
   *   non-conditionally throwing exceptions
   */

  /*
   * Tests detection of:
   * - Explicit `throw` statements
   * - Called methods that are annotated with @throws
   */
  "NX throwable detection" should {
    "find throw statements within methodSymbol blocks" in NX.unhandled {
      def doSomething (flag: Boolean) { if (flag) throw new IOException() }
    }.mustEqual(Set(classOf[IOException]))

    s"find throw statements within class 'primary constructors' (${specRef("5.3")}" in NX.unhandled {
      class MyClass (flag: Boolean) { if (flag) throw new IOException() }
    }.mustEqual(Set(classOf[IOException]))

    s"find throw statements within class 'auxiliary constructors' (${specRef("5.3.1")}" in NX.unhandled {
      class MyClass {
        def this (flag: Boolean) = {
          this()
          if (flag) throw new IOException()
        }
      }
    }.mustEqual(Set(classOf[IOException]))

    "find throw annotations on called Scala methods" in NX.unhandled {
      @throws[IOException]("") def thrower (): Unit = ()
      thrower()
    }.mustEqual(Set(classOf[IOException]))

    "find throw annotations on called Scala methods that use the Scala 2.9 @throws constructor" in NX.unhandled {
      @throws(classOf[IOException]) def thrower (): Unit = ()
      thrower()
    }.mustEqual(Set(classOf[IOException]))

    "transitively propagate @throw annotations from overridden methods when calling contravariantly" in NX.unhandled {
      trait A { @throws[IOException]() def thrower (): Unit }
      class B extends A { override def thrower (): Unit = () }
      val a:A = new B()
      a.thrower()
    }.mustEqual(Set(classOf[IOException]))

    "transitively propagate @throw annotations from overridden methods when calling covariantly" in NX.unhandled {
      trait A { @throws[IOException]() def thrower (): Unit }
      class B extends A { override def thrower (): Unit = throw new IOException() } /* But B's subclass can! */
      val b:B = new B()
      b.thrower()
    }.mustEqual(Set(classOf[IOException]))

    "find throw annotations on called Scala primary constructors" in NX.unhandled {
      class A @throws[IOException]() {}
      new A()
    }.mustEqual(Set(classOf[IOException]))

    "find throw annotations on called Scala auxiliary constructors" in NX.unhandled {
      class A (flag:Boolean) {
        @throws[IOException]() def this () = this(true)
      }
      new A()
    }.mustEqual(Set(classOf[IOException]))

    s"transitively propagate @throw annotations from Scala primary to auxiliary constructors" in NX.unhandled {
      class A @throws[IOException]() (flag:Boolean) {
        def this () = { this(true) }
      }

      /* Call a secondary constructor */
      new A()
    }.mustEqual(Set(classOf[IOException]))

    "find throw annotations on internal class constructors" in NX.unhandled {
      class A (flag:Boolean) {
        class B @throws[IOException]("") () {
          if (flag) throw new IOException()
        }
      }
      val a = new A(false)
      new a.B()
    }.mustEqual(Set(classOf[IOException]))

    "find throw annotations on Java methods" in NX.unhandled {
      /* Defined to throw an UnknownHostException */
      java.net.InetAddress.getByName("")
    }.mustEqual(Set(classOf[UnknownHostException]))
  }

  /*
   * Test @throws annotation handling on primary and auxiliary constructor.
   */
  "NX class-level @throws annotation handling" should {
    s"filter exactly matching 'primary constructor' annotations (${specRef("5.3")}) on the class primary constructor" in NX.unhandled {
      class A @throws[IOException]() (flag:Boolean) {
        if (flag) throw new IOException()
      }
    }.mustEqual(Set())

    s"filter subtype matching 'primary constructor' annotations (${specRef("5.3")}) on the class primary constructor" in NX.unhandled {
      class A @throws[Exception]() (flag:Boolean) {
        if (flag) throw new IOException()
      }
    }.mustEqual(Set())

    s"filter matching throwables (${specRef("5.3.1")}) on auxiliary constructors" in NX.unhandled {
      class A () {
        @throws[IOException]() def this (flag:Boolean) = {
          this()
          if (true) throw new IOException()
        }
      }
    }.mustEqual(Set())

    s"not propagate throwables from nested class definitions" in NX.unhandled {
      class A (flag:Boolean) {
        class B @throws[IOException]("") () {
          if (flag) throw new IOException()
        }
      }
    }.mustEqual(Set())
  }

  /*
   * Test @throws annotation handling on methods.
   */
  "NX def-level @throws annotation handling" should {
    "filter exactly matching throwables" in NX.unhandled {
      @throws[IOException]("explanation")
      def defExpr (flag:Boolean) = { if (!flag) throw new IOException() }
    }.mustEqual(Set())

    "filter subtype matching throwables" in NX.unhandled {
      @throws[IOException]("explanation")
      def defExpr (flag:Boolean) = { if (!flag) throw new IOException() }
    }.mustEqual(Set())

    "propagate non-matching throwables" in NX.unhandled {
      @throws[IOException]("explanation")
      def defExpr (flag:Boolean) = { if (!flag) throw new Exception() }
    }.mustEqual(Set(classOf[Exception]))
  }

  /*
   * Test try+catch analysis
   */
  "NX try() evaluation" should {
    "filter exactly matching throwables" in NX.unhandled {
      try { throw new IOException() } catch {
        case e:IOException => ()
      }
    }.mustEqual(Set())

    "filter subtype matching throwables" in NX.unhandled {
      try { throw new IOException() } catch {
        case e:Exception => ()
      }
    }.mustEqual(Set())

    "propagate non-matching throwables" in NX.unhandled {
      try { throw new Exception() } catch {
        case e:IOException => ()
      }
    }.mustEqual(Set(classOf[Exception]))

    "treat conditional catches as incomplete" in NX.unhandled {
      /* If there's a conditional, the case statement is necessarily treated as a non-match; there's
       * no way for us to known whether it will verifiably match all possible values at runtime. */
      try {
        throw new Exception()
      } catch {
        case e:IOException if e.getMessage == "conditional" => ()
      }
    }.mustEqual(Set(classOf[Exception]))

    "propagate all throwables within the catch block" in NX.unhandled {
      def defExpr (flag: Boolean) = {
        try {
          throw new IOException()
        } catch {
          case e:IOException => if (flag) throw new IOException
        }
      }
    }.mustEqual(Set(classOf[IOException]))

    "propagate all throwables within a conditional block" in NX.unhandled {
      def defExpr (flag: Boolean) = {
        try {
          throw new IOException()
        } catch {
          /* Define a condition that vends a UnknownHostException throwable */
          case e:IOException if InetAddress.getByName("test").isAnyLocalAddress => ()
          case e:IOException => ()
        }
      }
    }.mustEqual(Set(classOf[UnknownHostException]))
  }

 /*
  * Test escape analysis; verify that we've plugged any type gaps that would allow throwable annotations to be lost.
  */
  "NX escape analysis" should {
    "flag compile-time indeterminate case statements (eg, NonFatal(_)) asusableeable" in NX.unhandled {
      /*
       * Applicative matches provide an escape hatch; it's impossible to know how they
       * will match at runtime. Fortunately, if you're using checked exceptions, blanket
       * Throwable catches should *not* be necessary.
       */
      try {
        throw new Throwable()
      } catch {
        case NonFatal(e) => ()
      }
    }.mustEqual(Set(classOf[Throwable]))

    "flag single type contravariant @throws declarations on overridden methods" in NX.check {
      trait A {
        @throws[IOException] def doSomething (): Unit = {}
      }
      class B extends A {
        @throws[Exception]() override def doSomething (): Unit = throw new IOException()
      }
    }.errors.mustEqual(Seq(
      CannotOverride("doSomething", classOf[Exception])
    ))


    "flag contravariant widening of the exception types on overridden methods" in {
      NX.check {
        trait A {
          @throws[SocketException] def doSomething (): Unit = {}
        }
        class B extends A {
          @throws[UnknownHostException]() override def doSomething (): Unit = throw new UnknownHostException()
        }
      }.errors.mustEqual(Seq(
        CannotOverride("doSomething", classOf[UnknownHostException])
      ))
    }

    "permit covariant @throws declarations on overridden methods" in NX.check {
      trait A {
        @throws[Exception] def doSomething (): Unit = {}
      }
      class B extends A {
        @throws[IOException]() override def doSomething (): Unit = throw new IOException()
      }
    }.errors.mustEqual(Seq())

    "flag assignment-based @throws annotation erasure" in NX.check {
      @throws[IOException]() def thrower (flag: Boolean): Unit = if (flag) throw new IOException()
      val nonthrower: (Boolean => Unit) = thrower
    }.unhandled.mustEqual(Set(classOf[IOException]))

    "flag return-based @throws annotation erasure" in NX.check {
      @throws[IOException]() def thrower (flag: Boolean): Unit = if (flag) throw new IOException()
      type NonThrower = (Boolean => Unit)
      def foo (): NonThrower = {
        thrower
      }
    }.unhandled.mustEqual(Set(classOf[IOException]))
  }
}
