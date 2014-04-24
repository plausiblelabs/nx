No Exceptions: Checked Exceptions for Scala
==============

NX ("No Exceptions") is a Scala compiler plugin (2.10 and 2.11) that provides support for Java-style [checked exceptions](http://en.wikipedia.org/wiki/Exception_handling#Checked_exceptions).

_This is not an endorsement of the use of exceptions in Scala, checked or otherwise._

The double-meaning of the name is not an accident; the only thing worse than _checked_ exceptions are _unchecked_ exceptions, but we'd rather have _no exceptions_:

- Checked exceptions are as much part of a function's type as its successful return value. There should be **no exceptions** to compiler type checking.
- Exceptions are a horrendous way to express error conditions. We always prefer monadic approaches such as [Option](http://www.scala-lang.org/api/2.10.3/index.html#scala.Option) and [Either](http://www.scala-lang.org/api/2.10.3/index.html#scala.util.Either). There should be **no exceptions** used in non-Java code (and possibly even there).

Having compiler checked exceptions has been particularly valuable in finding bugs in code that interfaces with traditional exception-throwing Java APIs, and overall, we think that _if Scala is going to support exceptions at all_, compiler-checked exceptions are a net gain. Checked exceptions simply expose the true cost of exceptions.

We're using the plugin with our own code, and in the process of migration, discovered a number of bugs where we'd failed to handle error paths correctly. I expect such latent bugs to be relatively common in most Scala code bases.

Standard Setup
--------------

There are two ways to use NX:

* As a compiler plugin, which will check all code.
* As a macro, which can be used to check only specific code paths.

#### SBT

Enable the NX compiler plugin (if desired) in `build.sbt`:

    // Required until we can submit the plugin to Maven Central
    resolvers += "Plausible OSS" at "https://opensource.plausible.coop/nexus/content/repositories/public"

	addCompilerPlugin("coop.plausible" %% "no-exceptions" % "1.0.1")

If you want to use compile-time annotations and macros, add a library dependency to `built.sbt`:

	libraryDependencies ++= Seq(
		"coop.plausible"          %%  "no-exceptions"     % "1.0.1"
	)

#### Command Line

Pass `-Xplugin:...` to `scalac`:

	scalac -Xplugin:no-exceptions_2.10-1.0.1.jar source.scala


Standard Usage
-----------

By default, the plugin will apply the standard Java rules for checked and unchecked exceptions:

- All subclasses of `RuntimeException` are considered unchecked.
- All subclasses of `Error` are considered unechecked.

The default checked exception behavior can be adjusted with the `-P:nx:checked:` compiler argument:

- Default Java checked exception behavior is enabled with `-P:nx:checked:standard`.
- Strict checking (only subtypes of `Error` will be treated as unchecked) can be enabled with `-P:nx:checked:strict`
- Fatal checking (only VM "fatal" exceptions will be treated as unchecked -- VirtualMachineError, AssertionError, LinkageError) can be enabled with `-P:nx:checked:fatal`

As an alternative to the compiler plugin, you can use the `coop.plausible.nx.exceptionChecked` macro to perform compile-time validation of an explicit subset of your code:

	val address = exceptionChecked {
		InetAddresss.getByName(name)
	}

This operates identically to the compiler plugin, validating only the code within the checked block.

#### Declaring Thrown Exceptions

NX will automatically flag unhandled exceptions as compiler errors. These must either by caught via `try` blocks (see below), or declared to be thrown via Scala's standard [@throws](http://www.scala-lang.org/api/2.10.3/index.html#scala.throws) annotation.

The `@throws` behavior follows Scala's standard annotation behavior; we document some of the common usages below.

##### Annotating Methods

If annotated with `@throws`, a method's annotation applies to the entirity of the method, but *not* nested `def` statements:

	@throws[IOException]("if the file can not be opened")
	def openFile (path: Path): Result = { ... }


In addition, NX will not permit a `@throws`-annotated method to be assigned to a function-typed `val` or `var`, or returned as a function-typed value, as this will cause the exception typing to be lost. For example, the following will trigger an NX compile-time error:

	@throws[IOException]("if something goes pear shaped") def thrower: Unit = { ... }
    val nonthrower: (Boolean => Unit) = thrower
	

##### Annotating Class Constructors

If annotated with `@throws`, the primary class constructor's annotations apply to the entirity of the class initializer, including the class body and any `val` initializers -- but *not* `lazy val` initializers.

To annotate a class primary constructor with `@throws`:

	class AClass @throws[IOException]("if the file can not be opened") () {
	}

Any `@throws` annotations declared on the primary constructor will be automatically propagated to auxiliary constructors; auxilliary constructors can be further annotated using standard method annotations:

	class AClass @throws[IOException]("if the file can not be opened") (path: Path) {
		@throws[AdditionalException]
		def this() = { this("...") }
	}

#### Handling Exceptions With Try / Catch

The standard Scala try/catch mechanism works as expected, with one caveat: `catch` clauses based on runtime pattern matching cannot be statically validated, and will trigger an unhandled exception compiler error if the exception is not otherwise handled. For example, the following statement will treat IOException exception as unhandled:

	try {
		...
	} catch {
		case e:IOException if e.getMessage.startsWith("What is love?") => ...
	}

Non-static extractors, which rely on runtime evaluation, are also non-statically verifiable and are treated as unhandled. For example, Scala's `NonFatal` class will
not be considered a match for unhandled exceptions:

	try {
		...
	} catch {
		case NonFatal(e) => 
	}

In the future, we may add special-casing for specific classes -- such as NonFatal(e) -- that treat exceptions as handled if we can statically verify the exceptions will be caught via the classes' defined API. Patches to this effect are certainly welcome.

#### Asserting Non-Throwable Exceptions

If you have a pesky exception that you **know** can not be thrown, you can quiesce NX with the `coop.plausible.nx.assertNonThrows` macro:

    val bytes = assertNonThrows[UnsupportedEncodingException](string.getBytes("UTF-8"))

This will cause NX's checking to treat the given exception as handled; if the exception is thrown, it will be promoted to an [AssertionError](http://docs.oracle.com/javase/7/docs/api/java/lang/AssertionError.html) and rethrown.

#### Selectively Disabling Validation

Validation can also be turned off on a per-method and per-class basis by using the `coop.plausible.nx.UncheckedExceptions` annotation:

	/* Validation is disabled within the entire method */
	@UncheckedExceptions
	def livingDangerously (): Unit = {
	}

	/* Validation is disabled within the primary constructor */
	class LivingDangerously @UncheckedExceptions () {
	}
	
	/* Validation is disabled within the entire class */
	@UncheckedExceptions
	class LivingDangerously {
	}

**Warning:** This API should be considered experimental and subject to change:

* The plugin may be extended to consider an `@UncheckedExceptions` statement to also be a blanket `@throws[Throwable]`, and require an `catch` in any checked calling code.
* The behavior of `@UncheckedExceptions` may be modified to not apply to nested `def` statements within a method.
