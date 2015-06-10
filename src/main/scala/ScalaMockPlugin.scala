// Copyright (c) 2011 Paul Butcher
// 
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

import sbt._
import Keys._

object ScalaMockPlugin extends Plugin {
  
  lazy val GenerateMocks = config("generate-mocks") hide
  lazy val Mock = config("mock") extend(Compile)
  
  lazy val generatedMockDirectory = SettingKey[File]("generated-mock-directory", "Where generated mock source code will be placed")
  lazy val generatedTestDirectory = SettingKey[File]("generated-test-directory", "Where generated test source code will be placed")
  
  lazy val generateMocks = TaskKey[Unit]("generate-mocks", "Generates sources for classes with the @mock annotation")
  def generateMocksTask = (sources in GenerateMocks, classDirectory in GenerateMocks, scalacOptions in GenerateMocks, 
      classpathOptions, scalaInstance, fullClasspath in Compile, streams) map {
    (srcs, out, opts, cpOpts, si, cp, s) =>
      IO.delete(out)
      IO.createDirectory(out)
      val comp = new compiler.RawCompiler(si, cpOpts, s.log)
      comp(srcs, cp.files, out, opts)
  }
  
  def collectSource(directory: SettingKey[File]) =
    (directory, includeFilter in unmanagedSources, excludeFilter in unmanagedSources) map { 
      (d, f, e) => d.descendantsExcept(f, e).get
    }
  
  lazy val generateMocksSettings = 
    inConfig(GenerateMocks)(Defaults.configSettings) ++ 
    inConfig(Mock)(Defaults.configSettings) ++
    Seq(
      generatedMockDirectory <<= sourceManaged(_ / "mock" / "scala"),
      generatedTestDirectory <<= sourceManaged(_ / "test" / "scala"),
      scalacOptions in GenerateMocks <++= 
        (generatedMockDirectory, generatedTestDirectory, scalaVersion) map { (gm, gt, sv) =>
          Seq(
            "-Xplugin-require:scalamock",
            "-Ylog:generatemocks",
            if (sv startsWith "2.8") "-Ystop:superaccessors" else "-Ystop-after:generatemocks",
            "-P:scalamock:generatemocks:"+ gm,
            "-P:scalamock:generatetest:"+ gt)
        },
      sources in Test <++= collectSource(generatedTestDirectory),
      sources in Mock <++= collectSource(generatedMockDirectory),
      sources in Mock <++= collectSource(generatedTestDirectory),
      generateMocks <<= generateMocksTask dependsOn(compile in Compile),
      compile in Test <<= (compile in Test) dependsOn(compile in Mock),
      testOptions in Test <+= classDirectory in Mock map { dir =>
        Tests.Argument(TestFrameworks.ScalaTest, "-Dmock.classes=" + dir)
      })
}
