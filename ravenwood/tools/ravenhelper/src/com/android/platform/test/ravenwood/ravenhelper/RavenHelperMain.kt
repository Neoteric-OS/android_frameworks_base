@file:JvmName("RavenHelperMain")
package com.android.platform.test.ravenwood.ravenhelper


import com.android.hoststubgen.LogLevel
import com.android.hoststubgen.executableName
import com.android.hoststubgen.log
import com.android.hoststubgen.runMainWithBoilerplate
import com.android.tools.lint.UastEnvironment
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import java.io.File


fun main(args: Array<String>) {
    executableName = "RavenHelper"
    log.setConsoleLogLevel(LogLevel.Info)

    runMainWithBoilerplate {
        log.i("$executableName started")

        // val filePath = "/android/aosp-main-with-vendor-blobs2/frameworks/base/core/java/android/content/Intent.java"

        if (args.size == 0) {
            println("Usage: java ClassMethodLineNumbers FILES...")
            return
        }

        val files = args.map { File(it) }

        val env = UastEnvironment.create(UastEnvironment.Configuration.create(
            enableKotlinScripting = false,
            useFirUast = true,
        ))

        env.analyzeFiles(files)
        val javaPsiFacade = JavaPsiFacade.getInstance(env.ideaProject)
        val searchScope = GlobalSearchScope.everythingScope(env.ideaProject)

        val clazz = javaPsiFacade.findClass("android.content.Intent", searchScope)!! // Get NPE, TODO: figure it out

        clazz.methods.forEach { method ->
            System.out.println("${method.name}")


        }

        // val psiManager = PsiManager.getInstance(env.ideaProject)




        env.dispose()
        UastEnvironment.disposeApplicationEnvironment()


//        // val filePath = args[0]
//        // val sourceFile = File(filePath)
//
//        if (!sourceFile.exists() || !sourceFile.isFile) {
//            println("Error: Invalid file path provided.")
//            return
//        }
//
////        val source = Files.readString(sourceFile)
//        val code = String(Files.readAllBytes(Paths.get(filePath)))
//
//        // Initialize an empty project (headless environment)
//        val project = ProjectManager.getInstance().defaultProject
//
//        // Get the VirtualFile
////        val virtualFile =  VfsUtil.findFileByIoFile(sourceFile)
////            ?: run {
////                println("Error: Could not find VirtualFile for: $filePath")
////                return
//            }
//        // val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(sourceFile, true)
////        PsiFileFactory.getInstance(project).createFileFromText(code, Language.)
//
//        // Get the PsiFile from the VirtualFile
//        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
//        if (psiFile !is PsiJavaFile) {
//            println("Error: Not a Java file: $filePath")
//            return
//        }
//
//        // Visit classes and methods
//        psiFile.accept(object : JavaRecursiveElementVisitor() {
//            override fun visitClass(aClass: PsiClass) {
//                super.visitClass(aClass)
//                try {
//                    val lineNumber = getLineNumber(psiFile, aClass.textOffset)
//                    println("Class: ${aClass.qualifiedName} (Line: $lineNumber)")
//                } catch (e: IOException) {
//                    System.err.println("Error getting line number for class: ${aClass.qualifiedName}")
//                    e.printStackTrace()
//                }
//
//                // Print methods within the class
//                for (method in aClass.methods) {
//                    try {
//
//                        val methodLineNumber = getLineNumber(psiFile, method.textOffset)
//                        println("  Method: ${method.name} (Line: $methodLineNumber)")
//                    } catch (e: IOException) {
//                        System.err.println("Error getting line number for method: ${method.name}")
//                        e.printStackTrace()
//                    }
//                }
//            }
//
//            /* Redundant as methods are found by looking at methods within classes.
//            override fun visitMethod(method: PsiMethod) {
//                super.visitMethod(method)
//                try {
//                    val lineNumber = getLineNumber(psiFile, method.textOffset)
//                    println("Method: ${method.name} (Line: $lineNumber)")
//                } catch (e: IOException) {
//                    System.err.println("Error getting line number for method: ${method.name}")
//                    e.printStackTrace()
//                }
//            }
//            */
//        })
//
//    }
//}
//
//    fun getLineNumber(psiFile: PsiFile, offset: Int): Int {
//        val text = psiFile.text
//        var lineCount = 1
//        for (i in 0 until offset) {
//            if (text[i] == '\n') {
//                lineCount++
//            }
//        }
//        return lineCount
    }


    fun test() {
        // From Fe10UastEnvironment.kt
//        val fs = StandardFileSystems.local()
//        val psiManager = PsiManager.getInstance(ideaProject)
//        for (ktFile in ktFiles) {
//            val vFile = fs.findFileByPath(ktFile.absolutePath) ?: continue
//            val ktPsiFile = psiManager.findFile(vFile) as? KtFile ?: continue
//            ktPsiFiles.add(ktPsiFile)
//        }
    }


}
