package ch.epfl.bluebrain.nexus.rdf.bench
import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.rdf.IriParser
import ch.epfl.bluebrain.nexus.rdf.circe.JenaModel
import org.apache.jena.iri.IRIFactory
import org.openjdk.jmh.annotations.{Benchmark, Scope, State}
/**
  * Benchmark on Parsing
  * To run it, execute on the sbt shell: ''jmh:run -i 20 -wi 10 -f1 -t1 .*Parsing.*''
  * Which means "10 iterations" "10 warmup iterations" "1 fork" "1 thread"
  */
//noinspection TypeAnnotation
@State(Scope.Thread)
class Parsing {

  val iris = {
    import scala.collection.JavaConverters._
    val json  = jsonContentOf("/schema.json")
    val model = JenaModel(json).toOption.get

    val list = model.listStatements().asScala.foldLeft[List[String]](Nil) {
      case (acc, stmt) =>
        val subj = if (stmt.getSubject.isURIResource) List(stmt.getSubject.getURI) else Nil
        val pred = if (stmt.getPredicate.isURIResource) List(stmt.getPredicate.getURI) else Nil
        val obj  = if (stmt.getObject.isURIResource) List(stmt.getObject.asResource().getURI) else Nil
        subj ++ pred ++ obj ++ acc
    }
    println(s"IRIs: ${list.size}")
    list
  }

  val iriFactory = IRIFactory.iriImplementation()

  @Benchmark
  def parseIri(): Unit = {
    iris.foreach(i => new IriParser(i).parseAbsolute)
  }

  @Benchmark
  def parseAkkaUri(): Unit = {
    iris.foreach(Uri.apply)
  }

  @Benchmark
  def parseJenaIri(): Unit = {
    iris.foreach(i => {
      val iri = iriFactory.create(i)
      val _   = iri.violations(true)
    })
  }
}
