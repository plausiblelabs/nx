No Exceptions: Checked Exceptions for Scala
==============

NX ("No Exceptions") is a Scala compiler plugin (2.10 and 2.11) that provides support for Java-style [checked exceptions](http://en.wikipedia.org/wiki/Exception_handling#Checked_exceptions).

_This should not be considered an endorsement of the use of exceptions in Scala, checked or otherwise._

The double-meaning of the name is not an accident; the only thing worse than _checked_ exceptions are _unchecked_ exceptions, but we'd rather have _no exceptions_:

- Checked exceptions are as much part of a function's type as its successful return value. There should be **no exceptions** to compiler type checking.
- Exceptions are a horrendous way to express error conditions. We always prefer monadic approaches such as [Option](http://www.scala-lang.org/api/2.10.3/index.html#scala.Option) and [Either](http://www.scala-lang.org/api/2.10.3/index.html#scala.util.Either). There should be **no exceptions** used in non-Java code (and possibly even there).

Having compiler checked exceptions has been particularly valuable in finding bugs in code that interfaces with traditional exception-throwing Java APIs, and overall, we think that _if Scala is going to support exceptions at all_, compiler-checked exceptions are a net gain. Checked exceptions simply expose the true cost of exceptions.

We're using the plugin on our own code bases, and in the process of migration, discovered a number of bugs where we'd failed to handle error paths correctly. I expect such latent bugs to be relatively common in most Scala code bases.

Standard Setup
--------------

There are two ways to use NX:

* As a compiler plugin, which will check all code.
* As a macro, which can be used to check only specific code paths.

#### SBT

Enable the NX compiler plugin (if desired) in `build.sbt`:

    // Required until we can submit the plugin to Maven Central
    resolvers += "Plausible OSS" at "https://opensource.plausible.coop/nexus/content/repositories/public"

	addCompilerPlugin("coop.plausible" %% "no-exceptions" % "1.0")

If you want to use compile-time annotations and macros, add a library dependency to `built.sbt`:

	libraryDependencies ++= Seq(
		"coop.plausible"          %%  "no-exceptions"     % "1.0"
	)

#### Command Line

Pass `-Xplugin:...` to `scalac`:

	scalac -Xplugin:no-exceptions_2.10-1.0-SNAPSHOT.jar source.scala


Standard Usage
-----------

The compiler plugin will automatically flag unhandled exceptions as compiler errors. These can either by caught via `try` blocks, or declared to be thrown via Scala's standard [@throws](http://www.scala-lang.org/api/2.10.3/index.html#scala.throws) annotation.

Note that `catch` clauses based on runtime pattern matching cannot be statically validated, and will trigger an unhandled exception compiler error if the exception is not otherwise handled.

If you have a pesky exception that you **know** can not be thrown, you can quiesce NX with the `coop.plausible.nx.assertNonThrows` macro:

    val bytes = assertNonThrows[UnsupportedEncodingException](string.getBytes("UTF-8"))

By default, the plugin will apply the standard Java rules for checked and unchecked exceptions:

- All subclasses of `RuntimeException` are considered unchecked.
- All subclasses of `Error` are considered unechecked.

The default checked exception behavior can be adjusted with the `-P:nx:checked:` compiler argument:

- Default Java checked exception behavior is enabled with `-P:nx:checked:standard`.
- Strict checking (only subtypes of `Error` will be treated as unchecked) can be enabled with `-P:nx:checked:strict`
- Fatal checking (only VM "fatal" exceptions will be treated as unchecked -- VirtualMachineError, AssertionError, LinkageError) can be enabled with `-P:nx:checked:strictfatal`

### Macro Usage

With the library available, you can use `coop.plausible.nx.exceptionChecked` to perform compile-time validation of a subset of your code:

	val address = exceptionChecked {
		InetAddresss.getByName(name)
	}

