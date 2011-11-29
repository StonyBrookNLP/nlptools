package edu.washington.cs.knowitall
package tool
package parse
package pattern

import common._

import graph._
import collection._


/**
  * Represents a pattern with which graphs can be searched.  
  * A pattern will start and end with a node matcher, and every
  * matcher (necessarily) alternates between a NodeMatcher and
  * and EdgeMatcher.
  */
class Pattern[T](val matchers: List[Matcher[T]]) extends Function[Graph[T], List[Match[T]]] {
  // ensure that the matchers alternate
  matchers.view.zipWithIndex.foreach { case(m, i) => 
    (m, (i%2)) match {
      case (m: NodeMatcher[_], 0) =>
      case (m: EdgeMatcher[_], 1) =>
      case _ => throw new IllegalArgumentException("matchers must start with a node matcher and alternate")
    }
  }

  def this(edgeMatchers: List[EdgeMatcher[T]], nodeMatchers: List[NodeMatcher[T]]) = {
    this(enrich.Iterables.interleave(nodeMatchers, edgeMatchers).toList)
  }
  
  def apply(graph: Graph[T]): List[Match[T]] = {
    graph.vertices.toList.flatMap(apply(graph, _).toList)
  }

  def apply(graph: Graph[T], vertex: T): Option[Match[T]] = {
    def rec(matchers: List[Matcher[T]], 
      vertex: T, 
      edges: List[DirectedEdge[T]],
      groups: List[(String, T)]): Option[Match[T]] = matchers match {

      case (m: CaptureNodeMatcher[_]) :: xs =>
        if (m.matches(vertex)) rec(xs, vertex, edges, (m.alias, vertex) :: groups)
        else None
      case (m: NodeMatcher[_]) :: xs => 
        if (m.matches(vertex)) rec(xs, vertex, edges, groups)
        else None
      case (m: EdgeMatcher[_]) :: xs => 
        // only consider edges that have not been used
        val uniqueEdges = graph.dedges(vertex)--edges.flatMap(e => List(e, e.flip))
        // search for an edge that matches
        uniqueEdges.find(m.matches(_)).flatMap { dedge =>
          // we found one, so recurse
          rec(xs, dedge.end, dedge :: edges, groups)
        }
      case _ => Some(new Match(this, new Bipath(edges.reverse), groups.toMap))
    }

    rec(this.matchers, vertex, List(), List())
  }
  
  def edgeMatchers = matchers.collect { case m: EdgeMatcher[_] => m }
  def nodeMatchers = matchers.collect { case m: NodeMatcher[_] => m }

  def replaceMatcherAt(replacements: List[(Int, NodeMatcher[T])]) = 
    new Pattern(
      matchers.view.zipWithIndex.map {
        case (matcher, i) => replacements.find(_._1 == i).map(_._2) getOrElse matcher
      }.toList)
  
  def replaceMatcherAt(index: Int, replacement: NodeMatcher[T]) = 
    new Pattern(
      matchers.view.zipWithIndex.map {
        case (matcher, i) => if (i == index) replacement else matcher 
      }.toList)
  
  override def toString = {
    matchers.view.map(_.toString).mkString(" ")
  }
}

class Match[T](val pattern: Pattern[T], 
  val bipath: Bipath[T], 
  val groups: Map[String, T]) {
  override def toString = bipath.toString + ": " + groups.toString
}

abstract class Matcher[T]

/**
  * Trait to match dependency graph edges. 
  */
trait EdgeMatcher[T] extends Matcher[T] {
  def matches(edge: DirectedEdge[T]): Boolean
  def canMatch(edge: Graph.Edge[T]): Boolean = this.matches(new UpEdge(edge)) || this.matches(new DownEdge(edge))
}

/**
  * Trait to match dependency graph nodes. 
  */
trait NodeMatcher[T] extends Matcher[T] {
  def matches(node: T): Boolean
}

class TrivialNodeMatcher[T] extends NodeMatcher[T] {
  override def matches(edge: T) = true
  override def toString = ".*"
}

/**
  * Trait that captures the contents of a node if it's matched.
  * @param  alias  the name of the captured node
  * @param  matcher  the matcher to apply
  */
class CaptureNodeMatcher[T](val alias: String, val matcher: NodeMatcher[T]) 
extends NodeMatcher[T] {
  /**
    * Convenience constructor that uses the TrivialNodeMatcher.
    */
  def this(alias: String) = this(alias, new TrivialNodeMatcher)

  override def matches(node: T) = matcher.matches(node)
  override def toString = "{" + alias + "}"
}
