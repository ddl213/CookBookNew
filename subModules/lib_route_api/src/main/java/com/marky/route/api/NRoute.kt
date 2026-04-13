package com.marky.route.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.ActivityNavigator
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavOptions
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.get
import com.google.gson.Gson
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

class NRoute private constructor() {

    companion object {
        private val mDestinationMap = hashMapOf<String, Destination>()

        fun init(context: Context, navController: NavController) {

            val jsonList = parseNavFile(context)
            val destinationMap = hashMapOf<String, MutableList<Destination>>()

            destinationMap.put("activity", mutableListOf())
            destinationMap.put("fragment", mutableListOf())
            destinationMap.put("dialog", mutableListOf())
            jsonList.forEach { json ->
                if (json.isNotEmpty()) {
                    val jsonArray = JSONArray(json)
                    if (jsonArray.length() > 0) {
                        for (index in 0 until jsonArray.length()) {
                            val jsonObject = jsonArray.optJSONObject(index)
                            val path = jsonObject.optString("path")
                            val type = jsonObject.optString("type")
                            val className = jsonObject.optString("className")
                            val startPage = jsonObject.optBoolean("startPage")

                            val destination = Destination()
                            destination.let {
                                it.path = path
                                it.type = type
                                it.className = className
                                it.startPage = startPage
                            }


                            if (path.isNotEmpty() && className.isNotEmpty() && type.isNotEmpty()) {
                                destinationMap[type]?.add(destination)
                            }
                        }
                    }
                }

            }


            val activityNavigator = navController.navigatorProvider[ActivityNavigator::class]
            val fragmentNavigator = navController.navigatorProvider[FragmentNavigator::class]
            val dialogNavigator = navController.navigatorProvider[DialogFragmentNavigator::class]

            val navGraph = NavGraph(NavGraphNavigator(navController.navigatorProvider))


            destinationMap.entries.forEach {
                val s = it.key
                val destinations = it.value

                when (s) {
                    "activity" -> {
                        destinations.forEach { ld ->

                            mDestinationMap.put(ld.path, ld)
                            val destination = activityNavigator.createDestination()
                            destination.route = ld.path
                            destination.setComponentName(
                                ComponentName(
                                    context.packageName,
                                    ld.className
                                )
                            )
                            destination.addDeepLink(ld.path)
                            navGraph.addDestination(destination)

                            if (ld.startPage) {
                                navGraph.setStartDestination(ld.path)
                            }
                        }
                    }

                    "fragment" -> {
                        destinations.forEach { ld ->

                            mDestinationMap.put(ld.path, ld)
                            val destination = fragmentNavigator.createDestination()
                            destination.addDeepLink("${ld.path}")
                            destination.setClassName(ld.className)
                            destination.route = ld.path
                            navGraph.addDestination(destination)

                            if (ld.startPage) {
                                navGraph.setStartDestination(ld.path)
                            }
                        }

                    }

                    "dialog" -> {
                        destinations.forEach { ld ->

                            mDestinationMap.put(ld.path, ld)
                            val destination = dialogNavigator.createDestination()
                            destination.addDeepLink("${ld.path}")
                            destination.setClassName(ld.className)
                            destination.route = ld.path
                            navGraph.addDestination(destination)

                            if (ld.startPage) {
                                navGraph.setStartDestination(ld.path)
                            }
                        }
                    }
                }
            }


            destinationMap.clear()
            navController.setGraph(navGraph, null)

        }

        fun withNavController(navController: NavController) =
            RouteConfig.create().withNavController(navController)


        fun getArgument(originArgument: Bundle?, key: String): String {

            var value: String = ""

            if (originArgument != null) {
                val intent =
                    originArgument.getParcelable<Intent>("android-support-nav:controller:deepLinkIntent")
                intent?.data?.let { uri ->
                    value = uri.getQueryParameter(key) ?: ""
                }
            }

            return value
        }

        fun getArgument(originArgument: SavedStateHandle?, key: String): String {

            var value: String = ""

            if (originArgument != null) {
                val intent =
                    originArgument.get<Intent>("android-support-nav:controller:deepLinkIntent")
                intent?.data?.let { uri ->
                    value = uri.getQueryParameter(key) ?: ""
                }
            }

            return value
        }

        private fun parseNavFile(context: Context): List<String> {

            val jsons = mutableListOf<String>()
            val assets = context.resources.assets

            val list = assets.list("nav")
            try {
                if (list != null) {

                    for (item in list) {
                        if (item.contains("_nav")) {
                            jsons.add(parseFile(context, "nav/$item"))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return jsons
        }

        private fun parseFile(context: Context, s: String): String {
            try {
                val assets = context.resources.assets
                val open = assets.open(s)
                val stringBuilder = StringBuilder()
                val bufferedReader = BufferedReader(InputStreamReader(open))
                bufferedReader.use {
                    var line: String?
                    while (true) {
                        line = it.readLine() ?: break
                        stringBuilder.append(line)
                    }
                }
                return EncryptUtils.decrypt(stringBuilder.toString(), "marky")
            } catch (e: Exception) {
            }
            return ""
        }

        fun getFragment(path: String): String? {
            return mDestinationMap[path]?.className
        }

        fun generateDeeplink(path: String, arg: Bundle?): Uri {


            if (path.isEmpty()) {
                throw RuntimeException("Path is empty")
            }

            val deepLink = path.toUri().buildUpon()


            arg?.let { arg ->
                arg.keySet().forEach {
                    val value = arg.get(it)

                    if (value is Collection<*>) {
                        val toJson = Gson().toJson(value)
                        deepLink.appendQueryParameter(it, toJson)
                    } else {
                        deepLink.appendQueryParameter(it, value.toString())
                    }
                }

            }

            val finalDeepLink = deepLink.build()
            return finalDeepLink
        }

    }


    class RouteConfig private constructor() {
        private var mNavOption: NavOptions? = null
        private var mPath: String = ""
        private var mArgs: Bundle? = null
        private var mController: NavController? = null

        private var mCloseSelf = false

        companion object {


            fun create(): RouteConfig {
                return RouteConfig()
            }


        }

        fun withNavController(navController: NavController) = also {
            mController = navController
        }

        fun closeSelf(close: Boolean = true) = also {
            mCloseSelf = close
        }

        fun args(bundle: Bundle) = also {
            mArgs = bundle
        }

        fun deepLink(path: String) = also {
            mPath = path
        }

        fun navOptions(options: NavOptions) = also {
            mNavOption = options
        }

        fun go() {

            if (mController == null) {
                throw RuntimeException("NavController is null")
            }

            val finalDeepLink = if (mArgs != null) {
                generateDeeplink(mPath, mArgs)
            } else {
                Uri.parse(mPath)
            }

//            val finalDeepLink = generateDeeplink(mPath, mArgs)

            var navOptions: NavOptions? = null

            if (mCloseSelf) {
                val curPageRoute = mController!!.currentDestination?.route ?: ""
                if (curPageRoute.isNotEmpty()) {
                    navOptions = NavOptions.Builder().setPopUpTo(curPageRoute, true).build()
                }
            }

            if (mNavOption != null) {
                navOptions = mNavOption

            }
            mController!!.navigate(Uri.parse(finalDeepLink.toString())!!, navOptions)

        }


    }


}