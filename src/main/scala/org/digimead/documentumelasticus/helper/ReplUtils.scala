/*
Copyright 2010 Erik Engbrecht

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package org.digimead.documentumelasticus.helper

import java.lang.reflect._
import java.util.regex.Pattern
import scala.tools.scalap.Names

object ReplUtils {
  def isPublic(m: Member) = Modifier.isPublic(m.getModifiers)
  def isStatic(m: Member) = Modifier.isStatic(m.getModifiers)

  /**
   * Generate a list of all the methods on the class that could be considered to be "attributes"
   */
  def findAttributeMethods(cls: Class[_]): List[Method] = {
    val candidateMethods = cls.getMethods.filter(includeMethod _).toList.groupBy(_.getName)
    val methods = candidateMethods.flatMap(pair => removeMoreGeneralMethods(pair._2))
    methods.toList
  }

  def getAttributeValue(obj: AnyRef, attr: Method): Either[AnyRef, Throwable] = try {
    Left(attr.invoke(obj))
  } catch {
    case e: InvocationTargetException => Right(e.getCause)
  }

  /**
   * Build a map of the attribute values of the object.
   * Note that by identifying attributes by strings there is the potential to swallow attributes,
   * because there can be multiple methods of the same name but with unrelated return types.
   */
  def buildAttrValueMap(obj: AnyRef): Map[String, Either[AnyRef, Throwable]] = {
    val attrs = findAttributeMethods(obj.getClass)
    attrs.foldLeft(Map.empty[String, Either[AnyRef, Throwable]])((m, a) => m.updated(a.getName, getAttributeValue(obj, a)))
  }

  def diff(a: AnyRef, b: AnyRef): (Map[String, Either[AnyRef, Throwable]], Map[String, Either[AnyRef, Throwable]]) = {
    val aValues = buildAttrValueMap(a)
    val bValues = buildAttrValueMap(b)
    val commonKeys = aValues.keySet & bValues.keySet
    val aUniqueKeys = aValues.keySet &~ commonKeys
    val bUniqueKeys = bValues.keySet &~ commonKeys
    val (aDif, bDif) = commonKeys.foldLeft((Map.empty[String, Either[AnyRef, Throwable]], Map.empty[String, Either[AnyRef, Throwable]]))(
      (maps, key) => {
	val aVal = aValues(key)
	val bVal = bValues(key)
	(aVal, bVal) match {
	  case (Left(av), Left(bv)) if av == bv => maps
	  case (Right(ae), Right(be)) if ae.getClass == be.getClass && ae.getMessage == be.getMessage => maps
	  case _ => (maps._1.updated(key, aVal), maps._2.updated(key, bVal))
	}
    })
    val aResult = aDif ++ (aValues -- commonKeys)
    val bResult = bDif ++ (bValues -- commonKeys)
    (aResult, bResult)
  }

  def printDiff(a: AnyRef, b: AnyRef) {
    val (aDiff, bDiff) = diff(a, b)
    def p(obj: AnyRef, m: Map[String, Either[AnyRef, Throwable]]) {
      println(obj.toString + " (" + getSimpleName(obj.getClass) + ")")
      for((k, v) <- m) {
	val (t, s) = v match {
	  case Left(v) => if (v == null) ("", "null") else (": " + getSimpleName(v.getClass), v.toString)
	  case Right(e) => "<" + getSimpleName(e.getClass) + "> " + e.getMessage
	}
	println("\t" + k + t +  " = " + s)
      }
    }
    p(a, aDiff)
    p(b, bDiff)
  }

  /**
   * Print out the "attributes" of an object
   * The list of attributes is built by filtering down the public, zero-argument methods defined on the object.
   * Methods that are obviously either dangerous (likely to result in side effects) or uninteresting are not printed.
   * Output is in the format:
   *   <method name>: <declared teturn type> [(<actual return type>)] = <returned value as a string>
   * The actual return type will only be include if it is different from the declared return type.
   */
  def printAttrValues(obj: AnyRef): Unit = {
    for(method <- findAttributeMethods(obj.getClass).sortWith((a, b) => a.getName < b.getName)) {
      val (r, s) = getAttributeValue(obj, method) match {
	case Left(r) => (r, if (r == null) "null" else r.toString)
	case Right(e) => (null, "<" + e.getClass.getName +  "> " + e.getMessage)
      }
      val declaredType = getSimpleName(method.getReturnType)
      val actualType = if (r != null && r.getClass != method.getReturnType && boxMap.get(r.getClass) != Some(method.getReturnType)) {
			   " (" + getSimpleName(r.getClass) + ")"
		       } else ""
      val methodName = Names.decode(method.getName)
      println(methodName + ": " + declaredType + actualType + " = " + s)
    }
  }
  /** list of tuples (Boxed Class -> Unboxed Class) */
  val boxes = List[(Class[_], Class[_])](classOf[java.lang.Integer] -> classOf[Int], classOf[java.lang.Double] -> classOf[Double],
					 classOf[java.lang.Float] -> classOf[Float], classOf[java.lang.Long] -> classOf[Long],
					 classOf[java.lang.Short] -> classOf[Short], classOf[java.lang.Byte] -> classOf[Byte],
					 classOf[java.lang.Character] -> classOf[Char], classOf[java.lang.Boolean] -> classOf[Boolean])
  /** Map for checking is a Class boxes/unboxes to another class */
  val boxMap = boxes.foldLeft(Map.empty[Class[_], Class[_]])((m, t) => (m + t) + ((t._2, t._1))) 

  /**
   * Apply various criteria to determine if a method should be invoked and its value printed:
   * - the method has zero arguments
   * - the method is public
   * - the method is not static
   * - the method's name is not on the exclude list
   * - the method's return type is not on the exclude list
   * - the method is not a "magic" method
   */
  def includeMethod(m: Method) = {
    (m.getParameterTypes.length == 0) && isPublic(m) && !isStatic(m) && !excludeNames.contains(m.getName) && !isMagicMethod(m) &&
      includeReturnType(m.getReturnType)
  }
  /** names for methods that should be excluded when printing the attributes of an object */
  val excludeNames = Set("wait", "notify", "notifyAll", "toString", "clone", "finalize", "productPrefix", "productIterator",
		         "productElements")
  /**
   * Determine if a method should be included in printing based on the return type
   * This checks that the specified class is not a subclass of one of the classes in
   * excludedReturnTypes.  Examples of return types that shouldn't be printed include
   * Unit (void) and Nothing.
   */
  def includeReturnType(cls: Class[_]) = !excludedReturnTypes.exists(t => t.isAssignableFrom(cls))
  /**
   * Set of return types to use in filtering out methods.
   */
  val excludedReturnTypes = Set[Class[_]](classOf[Unit], classOf[Nothing])
  /**
   * Scala generates a lot of "magic" methods to implement functionality such as default arguments and specialization.
   * For example, the values for default method arguments are specified using specially named methods on the class.
   * For example, an automatically generated copy method on a case class will result in something like that following:
   *  - copy(String, Int, Int): TheClassType
   *  - copy$default$1: String
   *  - copy$default$2: Int
   *  - copy$default$3: Int
   * Methods that follow this naming pattern and detected so that they can be filtered out.
   */
  def isMagicMethod(m: Method) = {
    // need to decode the name first otherwise operator names, which aren't magic methods
    // may trigger the pattern to match
    magicMethodPat.matcher(Names.decode(m.getName)).matches
  }

  val magicMethodPat = Pattern.compile(".+\\$.+\\$.*")

  def isSameOrMoreSpecific(a: Class[_], b: Class[_]) = b.isAssignableFrom(a)

  /**
   * When a derived class overrides a method with a more-specific return type, this results
   * in there being two methods on the class - the original with the wide return type and the
   * new method with the narrower return type.  This is extremely common when implementing parameterized
   * traits, e.g. there will be one method return Object and another returing the specific type.
   * In general we're only interested in the fully refined methods.  This method removes the more general
   * methods from the list.  Most of the time this should just reduce the noise, but it may occassionally
   * strip out interesting methods.
   */
  def removeMoreGeneralMethods(methods: List[Method]): List[Method] = {
    def rec(working: List[Method], result: List[Method]): List[Method] = working match {
      case Nil => result
      case m :: xs => {
	if (methods.exists(e => e != m && isSameOrMoreSpecific(e.getReturnType, m.getReturnType))) rec(xs, result)
	else rec(xs, m :: result)
      }
    }
    rec(methods, Nil)
  }

  /**
   * Obtains the simple name of the class and gaurds against an exception that may be generated
   * due to a bug in the Scala compiler that causes it to produce malformed class names.
   */
  def getSimpleName(cls: Class[_]) = { 
    val base = try {
      cls.getSimpleName
    } catch {
      //scala occasionally produces malformed class names, especially in the REPL
      case e: InternalError if e.getMessage == "Malformed class name" => cls.getName
    }
    if (cls.isPrimitive) {
      base.substring(0, 1).toUpperCase + base.substring(1)
    } else Names.decode(base)
  }
}
