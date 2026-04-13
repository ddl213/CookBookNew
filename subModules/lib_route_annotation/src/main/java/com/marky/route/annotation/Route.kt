package com.marky.route.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class Route (val path : String, val startPage :Boolean = false){

}