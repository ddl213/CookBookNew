package com.marky.route.compiler.processor

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.marky.route.compiler.utils.EncryptUtils
import com.marky.route.compiler.utils.FileIOUtil
import java.io.File
import java.io.FileWriter

open class RouteProcessor(val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    private var mRouteMap: HashMap<String, JSONObject> = hashMapOf<String, JSONObject>()
    private val routeListJson = JSONArray()
    private val log = environment.logger
    private val MODULE_NAME = environment.options["MODULE_NAME"]
    private val ROOT_DIR = environment.options["ROOT_DIR"]


    override fun process(resolver: Resolver): List<KSAnnotated> {
        log.warn("start    $MODULE_NAME\n")
        val assertDir = File(ROOT_DIR, "/app/src/main/assets/nav")
        val navFile = File(assertDir, "_nav$MODULE_NAME")
        if (navFile.exists()) {
            val oldJson = FileIOUtil.readFile2String(navFile, null)
            if (oldJson.isNotEmpty()) {
                val oldJsonArray = JSON.parseArray(EncryptUtils.decrypt(oldJson, "marky"))

                routeListJson.addAll(oldJsonArray)

            }
        }


        resolver.getAllFiles()
            .toList().forEach { file ->
                file.declarations.filterIsInstance<KSClassDeclaration>().forEach { kclass ->
                    val ksAnnotation = kclass.annotations.toList()
                        .find { it.shortName.asString().contains("Route") }

                    if (ksAnnotation != null) {

                        var path = ""
                        var startPage = false
                        var type: String = ""
                        ksAnnotation.arguments.forEach { arg ->
                            when (arg.name?.asString() ?: "") {
                                "path" -> {
                                    path = arg.value as String
                                }

                                "startPage" -> {
                                    startPage = arg.value as Boolean
                                }
                            }
                        }
                        if (path.isNotEmpty()) {


                            val isActivity = checkSubType(kclass, "android.app.Activity")
                            val isFragment =
                                checkSubType(kclass, "androidx.fragment.app.Fragment")
                            val isDialog =
                                checkSubType(kclass, "androidx.fragment.app.DialogFragment")
                            if (isActivity) {
                                type = "activity"
                            }
                            if (isFragment) {
                                type = "fragment"
                            }
                            if (isDialog) {
                                type = "dialog"
                            }

                            val routeItemJson = JSONObject()
                            routeItemJson.put("path", path)
                            routeItemJson.put("type", type)
                            routeItemJson.put("startPage", startPage)
                            val targetClassName = kclass.qualifiedName?.asString()
                            routeItemJson.put("className", targetClassName)

                            routeListJson.add(routeItemJson)


                        }
                    }

                }
            }
        generateFile()

        return emptyList()
    }

    private fun checkSubType(ksClass: KSClassDeclaration, originType: String): Boolean {
        val superTypes = ksClass.superTypes.map { it.resolve() }
        for (superType in superTypes) {
            val superClassDeclaration = superType.declaration as? KSClassDeclaration
            if (superClassDeclaration != null) {
                val qualifiedName = superClassDeclaration.qualifiedName?.asString()
                if (qualifiedName == originType) {
                    return true
                }
                if (checkSubType(superClassDeclaration, originType)) {
                    return true
                }
            }
        }
        return false
    }

    private fun generateFile() {
        log.warn(" ---------------LKY-------------------\n")


        log.warn(routeListJson.toString())
        val assertDir = File(ROOT_DIR, "/app/src/main/assets/nav")
        val navFile = File(assertDir, "_nav$MODULE_NAME")

        if (!assertDir.exists()) {
            assertDir.mkdirs()
        }

        navFile.createNewFile()

        for (index in 0 until routeListJson.size) {
            val jsonObject = routeListJson.getJSONObject(index)
            val className = jsonObject.getString("className")
            if (className.isNotEmpty()) {
                mRouteMap.put(className, jsonObject)
            }
        }
        val routeValue = mRouteMap.entries.map { it.value }
        val finalJsonArray = JSONArray()
        routeValue.forEach { finalJsonArray.add(it) }


        val fileWriter = FileWriter(navFile)
        fileWriter.write(EncryptUtils.encrypt(finalJsonArray.toString(), "marky"))
        fileWriter.flush()
        fileWriter.close()



        log.warn("end    $MODULE_NAME\n")
        log.warn("---------------LKY-------------------\n")
    }

    override fun onError() {
        super.onError()
        log.warn("LKY fuck error")
    }
}




