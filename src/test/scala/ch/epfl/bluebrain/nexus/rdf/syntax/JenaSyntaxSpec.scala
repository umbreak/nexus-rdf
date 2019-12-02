package ch.epfl.bluebrain.nexus.rdf.syntax

import java.util.UUID

import cats.Id
import ch.epfl.bluebrain.nexus.rdf.Node.Literal
import ch.epfl.bluebrain.nexus.rdf.Node.Literal.LanguageTag
import ch.epfl.bluebrain.nexus.rdf.Vocabulary.xsd
import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.rdf.jena.JenaConversions._
import ch.epfl.bluebrain.nexus.rdf.{Graph, RdfSpec, RootedGraph}
import org.apache.jena.rdf.model.{Model, ModelFactory, ResourceFactory}

class JenaSyntaxSpec extends RdfSpec {

  "Jena syntax" should {

    "convert string literal to Jena model" in {
      literalToJenaLiteral("testLiteral") shouldEqual ResourceFactory.createStringLiteral("testLiteral")
    }

    "convert typed literal to Jena model" in {
      val jenaLiteral = literalToJenaLiteral(Literal("1999-04-09T20:00Z", url"http://schema.org/Date".value))
      jenaLiteral.getLexicalForm shouldEqual "1999-04-09T20:00Z"
      jenaLiteral.getDatatypeURI shouldEqual "http://schema.org/Date"
    }

    "convert literal with lang to Jena model" in {
      val jenaLiteral = literalToJenaLiteral(Literal("bonjour", LanguageTag("fr").toOption.get))
      jenaLiteral shouldEqual ResourceFactory.createLangLiteral("bonjour", "fr")
    }

    "convert IRI to Jena resource" in {
      val jenaResource = iriOrBNodeToResource(url"http://nexus.example.com/example-uri")
      jenaResource shouldEqual ResourceFactory.createResource("http://nexus.example.com/example-uri")
    }

    "convert blank node to Jena model" in {
      val id           = UUID.randomUUID().toString
      val jenaResource = iriOrBNodeToResource(b"$id")
      jenaResource.getId.getLabelString shouldEqual id
    }

    "convert property to Jena model" in {
      val jenaProperty = iriNodeToProperty(url"http://nexus.example.com/example-property")
      jenaProperty shouldEqual ResourceFactory.createProperty("http://nexus.example.com/example-property")
    }

    // format: off
    "convert Graph to Jena Model" in {
      val graph: Id[Model] = RootedGraph(url"http://nexus.example.com/john-doe",
        (url"http://nexus.example.com/john-doe", url"http://schema.org/name",                           "John Doe"),
        (url"http://nexus.example.com/john-doe", url"http://schema.org/birthDate",                      Literal("1999-04-09T20:00Z", url"http://schema.org/Date".value)),
        (url"http://nexus.example.com/john-doe", url"http://schema.org/birth",                          Literal("2002-05-30T09:00:00", xsd.string.value)),
        (url"http://nexus.example.com/john-doe", url"http://www.w3.org/1999/02/22-rdf-syntax-ns#type",  url"http://schema.org/Person")
      ).as[Model]()
      val model = ModelFactory.createDefaultModel()
      model.read(getClass.getResourceAsStream("/simple-model.json"), "http://nexus.example.com/", "JSONLD")
      val id = url"http://nexus.example.com/john-doe"
      graph.asGraph(id).rightValue.triples shouldEqual model.asGraph(id).rightValue.triples
    }
    // format: on

    "do not convert datatype string to XSD literal" in {
      val list = List("09:30:10.5", "09:00:00", "09:30:10Z", "09:30:10-06:00", "09:30:10+06:00")
      forAll(list) { time =>
        jenaToLiteral(ResourceFactory.createStringLiteral(time)).rightValue shouldEqual Literal(time)
      }
    }

    "convert IRI from Jena resource" in {
      toIriOrBNode(ResourceFactory.createResource("http://nexus.example.com/example-uri")).rightValue shouldEqual url"http://nexus.example.com/example-uri"
    }

    "fail to convert wrong IRI from Jena resource" in {
      toIriOrBNode(ResourceFactory.createResource("file:///some/path with space")).leftValue should startWith(
        "'file:///some/path with space' could not be converted to Iri."
      )
    }

    "convert blank node from Jena model" in {
      val jenaResource = ResourceFactory.createResource()
      val id           = jenaResource.getId.getLabelString

      toIriOrBNode(jenaResource).rightValue shouldEqual b"$id"
    }

    "convert property from Jena model" in {
      propToIriNode(ResourceFactory.createProperty("http://nexus.example.com/example-property")).rightValue shouldEqual url"http://nexus.example.com/example-property"
    }

    "fail to convert wrong property from Jena model" in {
      propToIriNode(ResourceFactory.createProperty("file:///some/path with space")).leftValue should startWith(
        "'file:///some/path with space' could not be converted to Iri."
      )
    }

    "convert Jena Model to Graph" in {
      val model = ModelFactory.createDefaultModel()
      model.read(getClass.getResourceAsStream("/simple-model.json"), "http://nexus.example.com/", "JSONLD")

      // format: off
      model.asGraph(url"http://nexus.example.com/john-doe").rightValue.triples shouldEqual Set[Graph.Triple](
        (url"http://nexus.example.com/john-doe", url"http://schema.org/name",                           "John Doe"),
        (url"http://nexus.example.com/john-doe", url"http://schema.org/birthDate",                      Literal("1999-04-09T20:00Z", url"http://schema.org/Date".value)),
        (url"http://nexus.example.com/john-doe", url"http://schema.org/birth",                          Literal("2002-05-30T09:00:00", xsd.string.value)),
        (url"http://nexus.example.com/john-doe", url"http://www.w3.org/1999/02/22-rdf-syntax-ns#type",  url"http://schema.org/Person")
      )
      // format: off
    }
  }
}
