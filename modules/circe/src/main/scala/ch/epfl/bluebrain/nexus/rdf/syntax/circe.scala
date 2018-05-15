package ch.epfl.bluebrain.nexus.rdf.syntax

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import ch.epfl.bluebrain.nexus.rdf.Graph
import ch.epfl.bluebrain.nexus.rdf.syntax.jena._
import io.circe.Json
import io.circe.parser._
import org.apache.jena.query.DatasetFactory
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.system.RiotLib
import org.apache.jena.riot.{JsonLDWriteContext, Lang, RDFDataMgr, RDFFormat}

import scala.util.Try

object circe {

  final implicit class CirceOps(private val json: Json) {

    /**
      * Convert Json-LD into [[Graph]]
      * @return [[Graph]] object created from given JSON-LD
      */
    def asGraph: Graph = {
      val model = ModelFactory.createDefaultModel()
      RDFDataMgr.read(model, new ByteArrayInputStream(json.noSpaces.getBytes), Lang.JSONLD)
      model
    }
  }

  final implicit class GraphOps(private val graph: Graph) {

    /**
      * Convert [[Graph]] into JSON-LD representation. Value of `@context` will be generated by Jena
      * @return [[Json]] containing JSON-LD representation of the [[Graph]]
      */
    def asJson: Json = {
      val out = new ByteArrayOutputStream()
      RDFDataMgr.write(out, graph, RDFFormat.JSONLD)
      parse(out.toString).getOrElse(Json.obj())
    }

    /**
      * Filter out context which are strings/iris as Jena doesn't  handle them. Other invalid contexts(booleans, numbers) etc.
      * will by handled by Jena and cause an error.
      *
      * @param context context to filter
      * @return Some if the context is not a string, None otherwise
      */
    private def filterIriContext(context: Json): Option[Json] = (context.asString, context.asArray) match {
      case (Some(_), _) => None
      case (_, Some(_)) => Some(context.mapArray(arr => arr.filterNot(_.isString)))
      case (_, _)       => Some(context)
    }

    /**
      * Convert [[Graph]] into JSON-LD representation using provided context. Beware, that currently IRI contexts are
      * not resolved and will be ignored.
      * @param context context to use when creating JSON-LD representation
      * @return [[Json]] containing JSON-LD representation of the [[Graph]]
      */
    def asJson(context: Json): Try[Json] =
      context.asObject.flatMap(_("@context")).flatMap(filterIriContext) match {
        case Some(c) =>
          val ctx = new JsonLDWriteContext
          ctx.setJsonLDContext(c.noSpaces)
          val g   = DatasetFactory.wrap(graph).asDatasetGraph
          val out = new ByteArrayOutputStream()
          val w   = RDFDataMgr.createDatasetWriter(RDFFormat.JSONLD)
          val pm  = RiotLib.prefixMap(g)
          Try {
            w.write(out, g, pm, null, ctx)
          }.flatMap(_ => parse(out.toString).toTry)
        case None => Try(asJson)
      }
  }
}
