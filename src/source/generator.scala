/**
  * Copyright 2014 Dropbox, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *    http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  * 
  * This file has been modified by Snap, Inc.
  */

package djinni

import djinni.ast._
import java.io._
import djinni.generatorTools._
import djinni.meta._
import djinni.syntax.Error
import djinni.writer.IndentWriter
import scala.language.implicitConversions
import scala.collection.mutable
import scala.util.matching.Regex

package object generatorTools {

  case class Spec(
                   javaOutFolder: Option[File],
                   javaPackage: Option[String],
                   javaClassAccessModifier: JavaAccessModifier.Value,
                   javaIdentStyle: JavaIdentStyle,
                   javaCppException: Option[String],
                   javaAnnotation: Option[String],
                   javaNullableAnnotation: Option[String],
                   javaNonnullAnnotation: Option[String],
                   javaImplementAndroidOsParcelable: Boolean,
                   javaUseFinalForRecord: Boolean,
                   javaGenInterface: Boolean,
                   cppOutFolder: Option[File],
                   cppHeaderOutFolder: Option[File],
                   cppIncludePrefix: String,
                   cppExtendedRecordIncludePrefix: String,
                   cppNamespace: String,
                   cppIdentStyle: CppIdentStyle,
                   cppFileIdentStyle: IdentConverter,
                   cppBaseLibIncludePrefix: String,
                   cppOptionalTemplate: String,
                   cppOptionalHeader: String,
                   cppEnumHashWorkaround: Boolean,
                   cppNnHeader: Option[String],
                   cppNnType: Option[String],
                   cppNnCheckExpression: Option[String],
                   cppUseWideStrings: Boolean,
                   jniOutFolder: Option[File],
                   jniHeaderOutFolder: Option[File],
                   jniIncludePrefix: String,
                   jniIncludeCppPrefix: String,
                   jniNamespace: String,
                   jniClassIdentStyle: IdentConverter,
                   jniFileIdentStyle: IdentConverter,
                   jniBaseLibIncludePrefix: String,
                   jniUseOnLoad: Boolean,
                   jniFunctionPrologueFile: Option[String],
                   cppExt: String,
                   cppHeaderExt: String,
                   objcOutFolder: Option[File],
                   objcppOutFolder: Option[File],
                   objcIdentStyle: ObjcIdentStyle,
                   objcFileIdentStyle: IdentConverter,
                   objcppExt: String,
                   objcHeaderExt: String,
                   objcIncludePrefix: String,
                   objcExtendedRecordIncludePrefix: String,
                   objcppIncludePrefix: String,
                   objcppIncludeCppPrefix: String,
                   objcppIncludeObjcPrefix: String,
                   objcppNamespace: String,
                   objcppFunctionPrologueFile: Option[String],
                   objcppDisableExceptionTranslation: Boolean,
                   objcBaseLibIncludePrefix: String,
                   objcSwiftBridgingHeaderWriter: Option[Writer],
                   objcSwiftBridgingHeaderName: Option[String],
                   objcGenProtocol: Boolean,
                   objcDisableClassCtor: Boolean,
                   objcClosedEnums: Boolean,
                   objcStrictProtocol: Boolean,
                   wasmOutFolder: Option[File],
                   wasmIncludePrefix: String,
                   wasmIncludeCppPrefix: String,
                   wasmBaseLibIncludePrefix: String,
                   wasmOmitConstants: Boolean,
                   wasmNamespace: Option[String],
                   wasmOmitNsAlias: Boolean,
                   jsIdentStyle: JsIdentStyle,
                   tsOutFolder: Option[File],
                   tsModule: String,
                   outFileListWriter: Option[Writer],
                   skipGeneration: Boolean,
                   yamlOutFolder: Option[File],
                   yamlOutFile: Option[String],
                   yamlPrefix: String,
                   moduleName: String)

  def useProtocol(ext: Ext, spec: Spec) = ext.objc || spec.objcGenProtocol

  def preComma(s: String) = {
    if (s.isEmpty) s else ", " + s
  }
  def q(s: String) = '"' + s + '"'
  def firstUpper(token: String) = if (token.isEmpty()) token else token.charAt(0).toUpper + token.substring(1)

  def leadingUpperStrict(token: String) = {
    if (token.isEmpty()) {
      token
    } else {
      val head = token.charAt(0)
      val tail = token.substring(1)
      // Preserve mixed case identifiers like 'XXFoo':
      // Convert tail to lowercase only when it is full uppercase.
      if (tail.toUpperCase == tail) {
        head.toUpper + tail.toLowerCase
      } else {
        head.toUpper + tail
      }
    }
  }

  type IdentConverter = String => String

  case class CppIdentStyle(ty: IdentConverter, enumType: IdentConverter, typeParam: IdentConverter,
                           method: IdentConverter, field: IdentConverter, local: IdentConverter,
                           enum: IdentConverter, const: IdentConverter)

  case class JavaIdentStyle(ty: IdentConverter, typeParam: IdentConverter,
                            method: IdentConverter, field: IdentConverter, local: IdentConverter,
                            enum: IdentConverter, const: IdentConverter)

  case class ObjcIdentStyle(ty: IdentConverter, typeParam: IdentConverter,
                            method: IdentConverter, field: IdentConverter, local: IdentConverter,
                            enum: IdentConverter, const: IdentConverter)

  case class JsIdentStyle(ty: IdentConverter, typeParam: IdentConverter,
                          method: IdentConverter, field: IdentConverter, local: IdentConverter,
                          enum: IdentConverter, const: IdentConverter)

  object IdentStyle {
    private val camelUpperStrict = (s: String) => {
        s.split("[-_]").map(leadingUpperStrict).mkString
    } 
    private val camelLowerStrict = (s: String) => {
      val parts = s.split('_')
      parts.head.toLowerCase + parts.tail.map(leadingUpperStrict).mkString
    }
    private val underLowerStrict = (s: String) => s.toLowerCase
    private val underUpperStrict = (s: String) => s.split('_').map(leadingUpperStrict).mkString("_")

    val camelUpper = (s: String) => s.split("[-_]").map(firstUpper).mkString
    val camelLower = (s: String) => {
      val parts = s.split('_')
      parts.head + parts.tail.map(firstUpper).mkString
    }
    val underLower = (s: String) => s
    val underUpper = (s: String) => s.split('_').map(firstUpper).mkString("_")
    val underCaps = (s: String) => s.toUpperCase
    val prefix = (prefix: String, suffix: IdentConverter) => (s: String) => prefix + suffix(s)

    val javaDefault = JavaIdentStyle(camelUpper, camelUpper, camelLower, camelLower, camelLower, underCaps, underCaps)
    val cppDefault = CppIdentStyle(camelUpper, camelUpper, camelUpper, underLower, underLower, underLower, underCaps, underCaps)
    val objcDefault = ObjcIdentStyle(camelUpper, camelUpper, camelLower, camelLower, camelLower, camelUpper, camelUpper)
    val jsDefault = JsIdentStyle(camelUpper, camelUpper, camelLower, camelLower, camelLower, underCaps, underCaps)

    val styles = Map(
      "FooBar" -> camelUpper,
      "fooBar" -> camelLower,
      "foo_bar" -> underLower,
      "Foo_Bar" -> underUpper,
      "FOO_BAR" -> underCaps,
      "FooBar!" -> camelUpperStrict,
      "fooBar!" -> camelLowerStrict,
      "foo_bar!" -> underLowerStrict,
      "Foo_Bar!" -> underUpperStrict,
      "FOO_BAR!" -> underCaps
    )
    
    def infer(input: String): Option[IdentConverter] = {
      styles.foreach((e) => {
        val (str, func) = e
        if (input endsWith str) {
          val diff = input.length - str.length
          return Some(if (diff > 0) {
            val before = input.substring(0, diff)
            prefix(before, func)
          } else {
            func
          })
        }
      })
      None
    }
  }

  object JavaAccessModifier extends Enumeration {
    val Public = Value("public")
    val Package = Value("package")

    def getCodeGenerationString(javaAccessModifier: JavaAccessModifier.Value): String = {
      javaAccessModifier match {
        case Public => "public "
        case Package => "/*package*/ "
      }
    }

  }
  implicit val javaAccessModifierReads: scopt.Read[JavaAccessModifier.Value] = scopt.Read.reads(JavaAccessModifier withName _)

  final case class SkipFirst() {
    private var first = true

    def apply(f: => Unit) {
      if (first) {
        first = false
      }
      else {
        f
      }
    }
  }

  case class GenerateException(message: String) extends java.lang.Exception(message)

  def createFolder(name: String, folder: File) {
    folder.mkdirs()
    if (folder.exists) {
      if (!folder.isDirectory) {
        throw new GenerateException(s"Unable to create $name folder at ${q(folder.getPath)}, there's something in the way.")
      }
    } else {
      throw new GenerateException(s"Unable to create $name folder at ${q(folder.getPath)}.")
    }
  }

  def generate(idl: Seq[TypeDecl], spec: Spec): Option[String] = {
    try {
      if (spec.cppOutFolder.isDefined) {
        if (!spec.skipGeneration) {
          createFolder("C++", spec.cppOutFolder.get)
          createFolder("C++ header", spec.cppHeaderOutFolder.get)
        }
        new CppGenerator(spec).generate(idl)
      }
      if (spec.javaOutFolder.isDefined) {
        if (!spec.skipGeneration) {
          createFolder("Java", spec.javaOutFolder.get)
        }
        new JavaGenerator(spec).generate(idl)
      }
      if (spec.jniOutFolder.isDefined) {
        if (!spec.skipGeneration) {
          createFolder("JNI C++", spec.jniOutFolder.get)
          createFolder("JNI C++ header", spec.jniHeaderOutFolder.get)
        }
        new JNIGenerator(spec).generate(idl)
      }
      if (spec.objcOutFolder.isDefined) {
        if (!spec.skipGeneration) {
          createFolder("Objective-C", spec.objcOutFolder.get)
        }
        new ObjcGenerator(spec).generate(idl)
      }
      if (spec.objcppOutFolder.isDefined) {
        if (!spec.skipGeneration) {
          createFolder("Objective-C++", spec.objcppOutFolder.get)
        }
        new ObjcppGenerator(spec).generate(idl)
      }
      if (spec.objcSwiftBridgingHeaderWriter.isDefined) {
        SwiftBridgingHeaderGenerator.writeAutogenerationWarning(spec.objcSwiftBridgingHeaderName.get, spec.objcSwiftBridgingHeaderWriter.get)
        SwiftBridgingHeaderGenerator.writeBridgingVars(spec.objcSwiftBridgingHeaderName.get, spec.objcSwiftBridgingHeaderWriter.get)
        new SwiftBridgingHeaderGenerator(spec).generate(idl)
      }
      if (spec.wasmOutFolder.isDefined) {
        if (!spec.skipGeneration) {
          createFolder("WASM", spec.wasmOutFolder.get)
        }
        new WasmGenerator(spec).generate(idl)
      }
      if (spec.tsOutFolder.isDefined) {
        if (!spec.skipGeneration) {
          createFolder("TypeScript", spec.tsOutFolder.get)
        }
        new TsGenerator(spec).generate(idl)
      }
      if (spec.yamlOutFolder.isDefined) {
        if (!spec.skipGeneration) {
          createFolder("YAML", spec.yamlOutFolder.get)
        }
        new YamlGenerator(spec).generate(idl)
      }
      None
    }
    catch {
      case GenerateException(message) => Some(message)
    }
  }

  sealed abstract class SymbolReference
  case class ImportRef(arg: String) extends SymbolReference // Already contains <> or "" in C contexts
  case class DeclRef(decl: String, namespace: Option[String]) extends SymbolReference
}

abstract class Generator(spec: Spec)
{
  protected val writtenFiles = mutable.HashMap[String,String]()

  protected def createFile(folder: File, fileName: String, makeWriter: OutputStreamWriter => IndentWriter, f: IndentWriter => Unit): Unit = {
    if (spec.outFileListWriter.isDefined) {
      spec.outFileListWriter.get.write(new File(folder, fileName).getPath + "\n")
    }
    if (spec.skipGeneration) {
      return
    }

    val file = new File(folder, fileName)
    val cp = file.getCanonicalPath
    writtenFiles.put(cp.toLowerCase, cp) match {
      case Some(existing) =>
        if (existing == cp) {
          throw GenerateException("Refusing to write \"" + file.getPath + "\"; we already wrote a file to that path.")
        } else {
          throw GenerateException("Refusing to write \"" + file.getPath + "\"; we already wrote a file to a path that is the same when lower-cased: \"" + existing + "\".")
        }
      case _ =>
    }

    val fout = new FileOutputStream(file)
    try {
      val out = new OutputStreamWriter(fout, "UTF-8")
      f(makeWriter(out))
      out.flush()
    }
    finally {
      fout.close()
    }
  }

  protected def createFile(folder: File, fileName: String, f: IndentWriter => Unit): Unit = createFile(folder, fileName, out => new IndentWriter(out), f)

  implicit def identToString(ident: Ident): String = ident.name
  val idCpp = spec.cppIdentStyle
  val idJava = spec.javaIdentStyle
  val idObjc = spec.objcIdentStyle
  val idJs = spec.jsIdentStyle

  protected def implToInterface(l: Impl): Interface = {
    val ext = Ext(false, true, false, false) // Only C++ implementations for now.
    val methods = l.methods.map(m => m.interface)
    return Interface(ext, methods, Seq.empty[Const])
  }

  def wrapNamespace(w: IndentWriter, ns: String, f: IndentWriter => Unit) {
    ns match {
      case "" => f(w)
      case s =>
        w.wl(s"namespace $s {").wl
        f(w)
        w.wl
        w.wl(s"} // namespace $s")
    }
  }

  def wrapAnonymousNamespace(w: IndentWriter, f: IndentWriter => Unit) {
    w.wl("namespace { // anonymous namespace")
    w.wl
    f(w)
    w.wl
    w.wl("} // end anonymous namespace")
  }

  def writeHppFileGeneric(folder: File, namespace: String, fileIdentStyle: IdentConverter)(name: String, origin: String, includes: Iterable[String], fwds: Iterable[String], f: IndentWriter => Unit, f2: IndentWriter => Unit) {
    createFile(folder, fileIdentStyle(name) + "." + spec.cppHeaderExt, (w: IndentWriter) => {
      w.wl("// AUTOGENERATED FILE - DO NOT MODIFY!")
      w.wl("// This file was generated by Djinni from " + origin)
      w.wl
      w.wl("#pragma once")
      if (includes.nonEmpty) {
        w.wl
        includes.foreach(w.wl)
      }
      w.wl
      wrapNamespace(w, namespace,
        (w: IndentWriter) => {
          if (fwds.nonEmpty) {
            fwds.foreach(w.wl)
            w.wl
          }
          f(w)
        }
      )
      f2(w)
    })
  }

  def writeCppFileGeneric(folder: File, namespace: String, fileIdentStyle: IdentConverter, includePrefix: String)(name: String, origin: String, includes: Iterable[String], f: IndentWriter => Unit) {
    createFile(folder, fileIdentStyle(name) + "." + spec.cppExt, (w: IndentWriter) => {
      w.wl("// AUTOGENERATED FILE - DO NOT MODIFY!")
      w.wl("// This file was generated by Djinni from " + origin)
      w.wl
      val myHeader = q(includePrefix + fileIdentStyle(name) + "." + spec.cppHeaderExt)
      w.wl(s"#include $myHeader  // my header")
      val myHeaderInclude = s"#include $myHeader"
      for (include <- includes if include != myHeaderInclude)
        w.wl(include)
      w.wl
      wrapNamespace(w, namespace, f)
    })
  }

  def generate(idl: Seq[TypeDecl]) {
    val decls = idl.collect { case itd: InternTypeDecl => itd }
    for (td <- decls) td.body match {
      case e: Enum =>
        assert(td.params.isEmpty)
        generateEnum(td.origin, td.ident, td.doc, e)
      case r: Record => generateRecord(td.origin, td.ident, td.doc, td.params, r)
      case i: Interface => generateInterface(td.origin, td.ident, td.doc, td.params, i)
      case l: Impl => generateImpl(td.origin, td.ident, td.doc, td.params, l)
      case p: ProtobufMessage => // never need to generate files for protobuf types
    }
    generateModule(decls.filter(td => td.body.isInstanceOf[Interface]))
  }

  def generateModule(decls: Seq[InternTypeDecl]) {}
  def generateEnum(origin: String, ident: Ident, doc: Doc, e: Enum)
  def generateRecord(origin: String, ident: Ident, doc: Doc, params: Seq[TypeParam], r: Record)
  def generateInterface(origin: String, ident: Ident, doc: Doc, typeParams: Seq[TypeParam], i: Interface)
  def generateImpl(origin: String, ident: Ident, doc: Doc, typeParams: Seq[TypeParam], i: Impl)

  // --------------------------------------------------------------------------
  // Render type expression

  def withNs(namespace: Option[String], t: String) = namespace match {
      case None => t
      case Some("") => "::" + t
      case Some(s) => "::" + s + "::" + t
    }

  def withCppNs(t: String) = withNs(Some(spec.cppNamespace), t)

  def writeAlignedCall(w: IndentWriter, call: String, params: Seq[Field], delim: String, end: String, f: Field => String): IndentWriter = {
    w.w(call)
    val skipFirst = new SkipFirst
    params.foreach(p => {
      skipFirst { w.wl(delim); w.w(" " * call.length()) }
      w.w(f(p))
    })
    w.w(end)
  }

  def writeAlignedCall(w: IndentWriter, call: String, params: Seq[Field], end: String, f: Field => String): IndentWriter =
    writeAlignedCall(w, call, params, ",", end, f)

  def writeAlignedObjcCall(w: IndentWriter, call: String, params: Seq[Field], end: String, f: Field => (String, String)) = {
    w.w(call)
    val skipFirst = new SkipFirst
    params.foreach(p => {
      val (name, value) = f(p)
      skipFirst { w.wl; w.w(" " * math.max(0, call.length() - name.length)); w.w(name)  }
      w.w(":" + value)
    })
    w.w(end)
  }

  def normalEnumOptions(e: Enum) = e.options.filter(_.specialFlag == None)

  def writeEnumOptionNone(w: IndentWriter, e: Enum, ident: IdentConverter, delim: String = "=") {
    for (o <- e.options.find(_.specialFlag == Some(Enum.SpecialFlag.NoFlags))) {
      writeDoc(w, o.doc)
      w.wl(ident(o.ident.name) + s" $delim 0,")
    }
  }

  def writeEnumOptions(w: IndentWriter, e: Enum, ident: IdentConverter, delim: String = "=") {
    var shift = 0
    for (o <- normalEnumOptions(e)) {
      writeDoc(w, o.doc)
      w.wl(ident(o.ident.name) + (if(e.flags) s" $delim 1 << $shift" else s" $delim $shift") + ",")
      shift += 1
    }
  }

  def writeEnumOptionAll(w: IndentWriter, e: Enum, ident: IdentConverter, delim: String = "=") {
    for (
      o <- e.options.find(_.specialFlag.contains(Enum.SpecialFlag.AllFlags))
    ) {
      writeDoc(w, o.doc)
      w.w(ident(o.ident.name) + s" $delim ")
      w.w(
        normalEnumOptions(e)
          .zipWithIndex
          .map{case(o, i) => s"(1 << $i)"}
          .fold("0")((acc, o) => acc + " | " + o)
      )
      w.wl(",")
    }
  }

  // --------------------------------------------------------------------------

  def writeMethodDoc(w: IndentWriter, method: Interface.Method, ident: IdentConverter) {
    val paramReplacements = method.params.map(p => (s"\\b${Regex.quote(p.ident.name)}\\b", s"${ident(p.ident.name)}"))
    val newDoc = Doc(method.doc.lines.map(l => {
      paramReplacements.foldLeft(l)((line, rep) =>
        line.replaceAll(rep._1, rep._2))
    }))
    writeDoc(w, newDoc)
  }

  def writeDoc(w: IndentWriter, doc: Doc) {
    doc.lines.length match {
      case 0 =>
      case 1 =>
        w.wl(s"/**${doc.lines.head} */")
      case _ =>
        w.wl("/**")
        doc.lines.foreach (l => w.wl(s" *$l"))
        w.wl(" */")
    }
  }
}
