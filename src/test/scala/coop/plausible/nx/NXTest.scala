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

package coop.plausible.nx

import org.specs2.mutable.Specification
import java.io.IOException
import java.net.{SocketException, InetAddress, UnknownHostException}
import scala.util.control.NonFatal
import coop.plausible.nx.ValidationResult.{UnhandledThrowable, CannotOverride}

import coop.plausible.nx

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
   * - The nx.verify() macro used below will elide all of the code within it from
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
    "find throw statements within methodSymbol blocks" in nx.verify {
      def doSomething (flag: Boolean) { if (flag) throw new IOException() }
    }.unhandled.mustEqual(Set(classOf[IOException]))

    s"find throw statements within class 'primary constructors' (${specRef("5.3")}" in nx.verify {
      class MyClass (flag: Boolean) { if (flag) throw new IOException() }
    }.unhandled.mustEqual(Set(classOf[IOException]))

    s"find throw statements within class 'auxiliary constructors' (${specRef("5.3.1")}" in nx.verify {
      class MyClass {
        def this (flag: Boolean) = {
          this()
          if (flag) throw new IOException()
        }
      }
    }.unhandled.mustEqual(Set(classOf[IOException]))

    "find throw annotations on called Scala methods" in nx.verify {
      @throws[IOException]("") def thrower (): Unit = ()
      thrower()
    }.unhandled.mustEqual(Set(classOf[IOException]))

    "find throw annotations on called Scala methods that use the Scala 2.9 @throws constructor" in nx.verify {
      @throws(classOf[IOException]) def thrower (): Unit = ()
      thrower()
    }.unhandled.mustEqual(Set(classOf[IOException]))

    "transitively propagate @throw annotations from overridden methods when calling contravariantly" in nx.verify {
      trait A { @throws[IOException]() def thrower (): Unit }
      class B extends A { override def thrower (): Unit = () }
      val a:A = new B()
      a.thrower()
    }.unhandled.mustEqual(Set(classOf[IOException]))

    "transitively propagate @throw annotations from overridden methods when calling covariantly" in nx.verify {
      trait A { @throws[IOException]() def thrower (): Unit }
      class B extends A { override def thrower (): Unit = throw new IOException() } /* But B's subclass can! */
      val b:B = new B()
      b.thrower()
    }.unhandled.mustEqual(Set(classOf[IOException]))

    "find throw annotations on called Scala primary constructors" in nx.verify {
      class A @throws[IOException]() {}
      new A()
    }.unhandled.mustEqual(Set(classOf[IOException]))

    "find throw annotations on called Scala auxiliary constructors" in nx.verify {
      class A (flag:Boolean) {
        @throws[IOException]() def this () = this(true)
      }
      new A()
    }.unhandled.mustEqual(Set(classOf[IOException]))

    s"transitively propagate @throw annotations from Scala primary to auxiliary constructors" in nx.verify {
      class A @throws[IOException]() (flag:Boolean) {
        def this () = { this(true) }
      }

      /* Call a secondary constructor */
      new A()
    }.unhandled.mustEqual(Set(classOf[IOException]))

    "find throw annotations on internal class constructors" in nx.verify {
      class A (flag:Boolean) {
        class B @throws[IOException]("") () {
          if (flag) throw new IOException()
        }
      }
      val a = new A(false)
      new a.B()
    }.unhandled.mustEqual(Set(classOf[IOException]))

    "find throw annotations on Java methods" in nx.verify {
      /* Defined to throw an UnknownHostException */
      java.net.InetAddress.getByName("")
    }.unhandled.mustEqual(Set(classOf[UnknownHostException]))

    "ignore non-throws annotations" in nx.verify {
      @inline @throws[IOException] def thrower (): Unit = ()
      thrower()
    }.unhandled.mustEqual(Set(classOf[IOException]))

    "find throwables within val definitions" in nx.verify {
      def thrower (flag: Boolean): Unit = {
        val b = { if (flag) throw new IOException() }
      }
    }.unhandled.mustEqual(Set(classOf[IOException]))

    "find throwables within var definitions" in nx.verify {
      def thrower (flag: Boolean): Unit = {
        var b = { if (flag) throw new IOException() }
      }
    }.unhandled.mustEqual(Set(classOf[IOException]))
  }

  /*
   * Test @throws annotation handling on primary and auxiliary constructor.
   */
  "NX class-level @throws annotation handling" should {
    s"filter exactly matching 'primary constructor' annotations (${specRef("5.3")}) on the class primary constructor" in nx.verify {
      class A @throws[IOException]() (flag:Boolean) {
        if (flag) throw new IOException()
      }
    }.unhandled.mustEqual(Set())

    s"filter subtype matching 'primary constructor' annotations (${specRef("5.3")}) on the class primary constructor" in nx.verify {
      class A @throws[Exception]() (flag:Boolean) {
        if (flag) throw new IOException()
      }
    }.unhandled.mustEqual(Set())

    s"filter matching throwables (${specRef("5.3.1")}) on auxiliary constructors" in nx.verify {
      class A () {
        @throws[IOException]() def this (flag:Boolean) = {
          this()
          if (true) throw new IOException()
        }
      }
    }.unhandled.mustEqual(Set())

    s"not propagate throwables from nested class definitions" in nx.verify {
      class A (flag:Boolean) {
        class B @throws[IOException]("") () {
          if (flag) throw new IOException()
        }
      }
    }.unhandled.mustEqual(Set())

    "find throwables within val initializers" in nx.verify {
      class A (flag: Boolean) {
        val b = { if (flag) throw new IOException() }
      }
    }.errors.mustEqual(Seq(UnhandledThrowable(classOf[IOException])))

    "find throwables within lazy val initializers" in nx.verify {
      /* The primary constructor @throws annotation can not cover lazy val initializers, as they will be invoked
       * on first access. */
      class A @throws[IOException]() (flag: Boolean) {
        lazy val b = { if (flag) throw new IOException() }
      }
    }.errors.mustEqual(Seq(UnhandledThrowable(classOf[IOException])))


    "filter val initializer throwables that match the primary constructor @throws annotations" in nx.verify {
      class A @throws[IOException]() (flag: Boolean) {
        val b = { if (flag) throw new IOException() }
      }
    }.errors.mustEqual(Seq())

  }

  /*
   * Test @throws annotation handling on methods.
   */
  "NX def-level @throws annotation handling" should {
    "filter exactly matching throwables" in nx.verify {
      @throws[IOException]("explanation")
      def defExpr (flag:Boolean) = { if (!flag) throw new IOException() }
    }.unhandled.mustEqual(Set())

    "filter subtype matching throwables" in nx.verify {
      @throws[IOException]("explanation")
      def defExpr (flag:Boolean) = { if (!flag) throw new IOException() }
    }.unhandled.mustEqual(Set())

    "propagate non-matching throwables" in nx.verify {
      @throws[IOException]("explanation")
      def defExpr (flag:Boolean) = { if (!flag) throw new Exception() }
    }.unhandled.mustEqual(Set(classOf[Exception]))
  }

  /*
   * Test inherited @throws annotation handling
   */
  "NX inheritance validation" should {
    "flag single type contravariant @throws declarations on overridden methods" in nx.verify {
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
      nx.verify {
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

    "permit covariant @throws declarations on overridden methods" in nx.verify {
      trait A {
        @throws[Exception] def doSomething (): Unit = {}
      }
      class B extends A {
        @throws[IOException]() override def doSomething (): Unit = throw new IOException()
      }
    }.errors.mustEqual(Seq())

    /* Even if unchecked, @throws changes the method signature; implementors should be warned of the widening
     * of the signature caused by declaring a runtime @throws */
    "flag contravariant widening of unchecked exception types on overridden methods" in nx.verify {
      trait A {
       def doSomething (): Unit = {}
      }
      class B extends A {
        @throws[RuntimeException]() override def doSomething (): Unit = throw new RuntimeException()
      }
    }.errors.mustEqual(Seq(CannotOverride("doSomething", classOf[RuntimeException])))
  }

  /*
   * Test try+catch analysis
   */
  "NX try() evaluation" should {
    "filter exactly matching throwables" in nx.verify {
      try { throw new IOException() } catch {
        case e:IOException => ()
      }
    }.unhandled.mustEqual(Set())

    "filter subtype matching throwables" in nx.verify {
      try { throw new IOException() } catch {
        case e:Exception => ()
      }
    }.unhandled.mustEqual(Set())

    "propagate non-matching throwables" in nx.verify {
      try { throw new Exception() } catch {
        case e:IOException => ()
      }
    }.unhandled.mustEqual(Set(classOf[Exception]))

    "treat conditional catches as incomplete" in nx.verify {
      /* If there's a conditional, the case statement is necessarily treated as a non-match; there's
       * no way for us to known whether it will verifiably match all possible values at runtime. */
      try {
        throw new Exception()
      } catch {
        case e:IOException if e.getMessage == "conditional" => ()
      }
    }.unhandled.mustEqual(Set(classOf[Exception]))

    "propagate all throwables within the catch block" in nx.verify {
      def defExpr (flag: Boolean) = {
        try {
          throw new IOException()
        } catch {
          case e:IOException => if (flag) throw new IOException
        }
      }
    }.unhandled.mustEqual(Set(classOf[IOException]))

    "propagate all throwables within a conditional block" in nx.verify {
      def defExpr (flag: Boolean) = {
        try {
          throw new IOException()
        } catch {
          /* Define a condition that vends a UnknownHostException throwable */
          case e:IOException if InetAddress.getByName("test").isAnyLocalAddress => ()
          case e:IOException => ()
        }
      }
    }.unhandled.mustEqual(Set(classOf[UnknownHostException]))
  }

 /*
  * Test escape analysis; verify that we've plugged any type gaps that would allow throwable annotations to be lost.
  */
  "NX escape analysis" should {
    "flag compile-time indeterminate case statements (eg, Fatal(_)) as unusable" in nx.verify {
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
    }.unhandled.mustEqual(Set(classOf[Throwable]))

    "flag assignment-based @throws annotation erasure" in nx.verify {
      @throws[IOException]() def thrower (flag: Boolean): Unit = if (flag) throw new IOException()
      val nonthrower: (Boolean => Unit) = thrower
    }.unhandled.mustEqual(Set(classOf[IOException]))

    "flag return-based @throws annotation erasure" in nx.verify {
      @throws[IOException]() def thrower (flag: Boolean): Unit = if (flag) throw new IOException()
      type NonThrower = (Boolean => Unit)
      def foo (): NonThrower = {
        thrower
      }
    }.unhandled.mustEqual(Set(classOf[IOException]))
  }

  "Unchecked exception handling" should {
    s"filter RuntimeException and Error subclasses when defaulted to ${CheckedExceptionConfig.Standard}" in nx.verify {
      def throw1 (flag: Boolean) = if (flag) throw new IOException()
      def throw2 (flag: Boolean) = if (flag) throw new RuntimeException()
      def throw3 (flag: Boolean) = if (flag) throw new Error()
    }.unhandled.mustEqual(Set(classOf[IOException]))

    s"filter RuntimeException and Error subclasses when ${CheckedExceptionConfig.Standard} is enabled" in nx.verify(CheckedExceptionConfig.Standard) {
      def throw1 (flag: Boolean) = if (flag) throw new IOException()
      def throw2 (flag: Boolean) = if (flag) throw new RuntimeException()
      def throw3 (flag: Boolean) = if (flag) throw new Error()
    }.unhandled.mustEqual(Set(classOf[IOException]))

    s"filter Error subclasses when ${CheckedExceptionConfig.Strict} is enabled" in nx.verify(CheckedExceptionConfig.Strict) {
      def throw1 (flag: Boolean) = if (flag) throw new IOException()
      def throw2 (flag: Boolean) = if (flag) throw new RuntimeException()
      def throw3 (flag: Boolean) = if (flag) throw new Error()
    }.unhandled.mustEqual(Set(classOf[IOException], classOf[RuntimeException]))

    s"filter only fatal exceptions when ${CheckedExceptionConfig.Fatal} is enabled" in nx.verify(CheckedExceptionConfig.Fatal) {
      def throw1 (flag: Boolean) = if (flag) throw new IOException()
      def throw2 (flag: Boolean) = if (flag) throw new RuntimeException()
      def throw3 (flag: Boolean) = if (flag) throw new Error()
      def throw4 (flag: Boolean) = if (flag) throw new OutOfMemoryError()
      def throw5 (flag: Boolean) = if (flag) throw new LinkageError()
      def throw6 (flag: Boolean) = if (flag) throw new AssertionError()
    }.unhandled.mustEqual(Set(classOf[IOException], classOf[RuntimeException], classOf[Error]))
  }

  "@UncheckedExceptions annotation handling" should {
    "disable validation on methods" in nx.verify {
      @UncheckedExceptions
      def thrower (flag: Boolean) = if (flag) throw new Exception()
    }.errors.mustEqual(Seq())

    "disable validation on class-level initializers via the primary constructor" in nx.verify {
      class A @UncheckedExceptions() (flag: Boolean) {
        if (flag) throw new Exception()
      }
    }.errors.mustEqual(Seq())

    "disable validation on entire classes" in nx.verify {
      @UncheckedExceptions class A (flag: Boolean) {
        def thrower (flag: Boolean) = if (flag) throw new Exception()
      }
    }.errors.mustEqual(Seq())
  }

  "macro assertion" should {
    "re-raise the specified exception as an assertion" in {
      def thrower (flag: Boolean): Unit = nx.assertNoThrows[IOException] {
        if (flag) throw new IOException
      }
      thrower(true) must throwA[AssertionError]
    }

    "return the expected result on non-failure" in {
      def thrower (): Int = nx.assertNoThrows[IOException] { 42 }
      thrower() mustEqual(42)
    }
  }
}
