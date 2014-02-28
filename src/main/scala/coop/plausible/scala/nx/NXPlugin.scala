import scala.tools.nsc.Global
import scala.tools.nsc.plugins.{PluginComponent, Plugin}

/**
 * No Exceptions Plugin
 * @param global Compiler state.
 */
class NXPlugin (val global: Global) extends Plugin {
  override val name: String = "noex"
  override val description: String = "Checked exceptions for Scala. If you're stuck with exceptions, they should be checked exceptions."
  override val components: List[PluginComponent] = List()
}
