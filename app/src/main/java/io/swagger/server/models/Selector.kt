/**
* CulebraTester
* ## Snaky Android Test --- If you want to be able to try out the API using the **Execute** or **TRY** button from this page - an android device should be connected using `adb` - the server should have been started using `./culebratester2 start-server`  then you will be able to invoke the API and see the responses. 
*
* OpenAPI spec version: 2.0.29
* 
*
* NOTE: This class is auto generated by the swagger code generator program.
* https://github.com/swagger-api/swagger-codegen.git
* Do not edit the class manually.
*/package io.swagger.server.models


/**
 *  * @param checkable  * @param clazz  * @param clickable  * @param depth  * @param desc  * @param pkg  * @param res  * @param scrollable  * @param text  * @param index  * @param instance */
data class Selector (    val checkable: kotlin.Boolean? = null,    val clazz: kotlin.String? = null,    val clickable: kotlin.Boolean? = null,    val depth: kotlin.Int? = null,    val desc: kotlin.String? = null,    val pkg: kotlin.String? = null,    val res: kotlin.String? = null,    val scrollable: kotlin.Boolean? = null,    val text: kotlin.String? = null,    val index: kotlin.Int? = null,    val instance: kotlin.Int? = null
) {
}
