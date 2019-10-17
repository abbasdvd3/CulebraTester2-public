package com.dtmilano.android.culebratester2

import android.annotation.SuppressLint
import android.os.Build
import android.util.JsonWriter
import android.util.Log
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.StringWriter
import java.util.*
import java.util.regex.Pattern
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory
import kotlin.collections.HashMap


private const val DEBUG = false
private const val TAG = "WindowHierarchyDump"

/**
 * Ellipsize a string.
 */
fun ellipsize(s: String, max: Int = 30): String {
    val l = s.length
    if (l > max) {
        return s.substring(0, max) + "…"
    }
    return s
}

@Throws(ConvertDumpToJsonException::class)
fun convertWindowHierarchyDumpToJson(dump: String): String {
    if (DEBUG) {
        Log.w(TAG, "Should convert ${ellipsize(dump)} to JSON")
    }
    // FIXME: we don't need a newInstance() every time...
    val spf = SAXParserFactory.newInstance()
    val handler = WindowHierarchyDumpToJsonHandler()
    val parser: SAXParser?
    val inputStream = ByteArrayInputStream(dump.toByteArray(Charsets.UTF_8))
    try {
        parser = spf.newSAXParser()
        val reader = parser!!.xmlReader
        reader.contentHandler = handler
        parser.parse(inputStream, handler)
    } catch (e: ParserConfigurationException) {
        Log.e(TAG, "Error creating parser:", e)
        throw ConvertDumpToJsonException(e)
    } catch (e: SAXException) {
        Log.e(TAG, "Exception in parser", e)
        throw ConvertDumpToJsonException(e)
    } catch (e: IOException) {
        Log.e(TAG, "IO Error", e)
        throw ConvertDumpToJsonException(e)
    }

    return handler.json
}

/**
 * Created by diego on 2015-10-29.
 */
class WindowHierarchyDumpToJsonHandler : DefaultHandler() {

    private var mWriter: StringWriter? = null
    private var mJsonWriter: JsonWriter? = null
    private var mNodeCount: Int = 0
    private var mArrayCount: Int = 0
    private var mUniqueId = -1
    private val mParents = Stack<Int>()

    /**
     * Gets the JSON string.
     *
     * @return
     */
    val json: String
        get() = mWriter!!.toString()

    @SuppressLint("LongLogTag")
    @Throws(SAXException::class)
    override fun endDocument() {
        if (DEBUG) {
            Log.d(TAG, "endDocument")
        }
        super.endDocument()
        try {
            mJsonWriter!!.close()
            mWriter!!.close()
            if (DEBUG) {
                Log.d(TAG, "JSON: ${ellipsize(mWriter.toString())}")
            }
        } catch (e: IOException) {
            throw SAXException(e)
        }

    }

    @SuppressLint("LongLogTag")
    @Throws(SAXException::class)
    override fun startDocument() {
        if (DEBUG) {
            Log.d(TAG, "startDocument")
        }
        super.startDocument()
        mWriter = StringWriter()
        mJsonWriter = JsonWriter(mWriter)
        mNodeCount = 0
        mArrayCount = 0
    }

    @SuppressLint("LongLogTag")
    @Throws(SAXException::class)
    override fun endElement(uri: String, localName: String, qName: String) {
        if (DEBUG) {
            Log.d(TAG, "endElement: $localName $qName $mNodeCount $mArrayCount")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val s = mWriter.toString()
                Log.d(TAG, "endElement: ${ellipsize(s)}")
            }
        }
        when (localName) {
            "hierarchy" -> try {
                mJsonWriter!!.endArray()
                mJsonWriter!!.endObject()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            "node" -> try {
                mJsonWriter!!.endArray()
                mArrayCount--
                mParents.pop()
                mJsonWriter!!.endObject()
                mNodeCount--
            } catch (e: IOException) {
                throw SAXException(e)
            } catch (e: IllegalStateException) {
                throw SAXException(e)
            }

            else -> Log.w(TAG, "endElement: $localName not handled")
        }

    }

    @SuppressLint("LongLogTag")
    @Throws(SAXException::class)
    override fun startElement(
        uri: String,
        localName: String,
        qName: String,
        attributes: Attributes
    ) {
        if (DEBUG) {
            Log.d(TAG, "startElement: $uri")
            Log.d(
                TAG,
                "startElement: " + localName + " " + attributes.getValue("resource-id") + " " + attributes.getValue(
                    "class"
                ) + " " + mNodeCount + " " + mArrayCount
            )
            Log.d(TAG, "startElement: parent=$mParents")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val s = mWriter.toString()
                Log.d(TAG, "startElement: ${ellipsize(s)}")
            }
        }
        when (localName) {
            "hierarchy" -> try {
                mJsonWriter!!.beginObject()
                mJsonWriter!!.name("id").value("hierarchy")
                mJsonWriter!!.name("text").value("Window Hierarchy")
                mJsonWriter!!.name("children")
                mJsonWriter!!.beginArray()
                mUniqueId = 0
                mParents.push(-1)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            "node" -> try {
                mJsonWriter!!.beginObject()
                mNodeCount++
                // resource-id's are not unique (i.e. listviews) then we cannot use it as the id for the tree
                mJsonWriter!!.name("id").value(mUniqueId)
                mJsonWriter!!.name("parent").value(mParents.peek())
                if (DEBUG) {
                    Log.d(TAG, "startElement: node: id=" + mUniqueId + " parent=" + mParents.peek())
                }
                // FIXME: change this
                // text is jstree's label, the original text is stored under __text
                val text = attributes.getValue("text")
                mJsonWriter!!.name("text").value(
                    attributes.getValue("class") + "_" + attributes.getValue("resource-id") + "_" + (if (text != null && text.length > 20) text.substring(
                        0,
                        17
                    ) + "..." else text) + " id=" + mUniqueId + " parent=" + mParents.peek()
                )
                for (xmlAttribute in XML_TO_JSON_MAP.keys) {
                    val jsonPropertyDescription = XML_TO_JSON_MAP[xmlAttribute]
                    when (jsonPropertyDescription?.mType) {
                        "int" -> {
                            if (DEBUG) {
                                Log.d(
                                    TAG,
                                    "startElement: int: " + jsonPropertyDescription.mName + " = " + Integer.parseInt(
                                        attributes.getValue(xmlAttribute)
                                    )
                                )
                            }
                            mJsonWriter!!.name(jsonPropertyDescription.mName)
                                .value(Integer.parseInt(attributes.getValue(xmlAttribute)))
                        }

                        "boolean" -> mJsonWriter!!.name(jsonPropertyDescription.mName).value(
                            java.lang.Boolean.parseBoolean(
                                attributes.getValue(xmlAttribute)
                            )
                        )

                        "String" -> mJsonWriter!!.name(jsonPropertyDescription.mName).value(
                            attributes.getValue(xmlAttribute)
                        )
                        else -> mJsonWriter!!.name(jsonPropertyDescription?.mName).value(
                            attributes.getValue(
                                xmlAttribute
                            )
                        )
                    }
                }
                // python:
                //bounds = re.split('[\][,]', attributes['bounds'])
                //attributes['bounds'] = ((int(bounds[1]), int(bounds[2])), (int(bounds[4]), int(bounds[5])))
                val ba = attributes.getValue("bounds")
                if (ba != null) {
                    val bounds = ba.split(("[" + Pattern.quote("][,") + "]").toRegex())
                        .dropLastWhile { it.isEmpty() }.toTypedArray()
                    mJsonWriter!!.name("bounds").beginArray()
                    for (i in intArrayOf(1, 2, 4, 5)) {
                        mJsonWriter!!.value(Integer.valueOf(bounds[i]))
                    }
                    mJsonWriter!!.endArray()
                }
                mJsonWriter!!.name("children").beginArray()
                mParents.push(mUniqueId)
                mUniqueId++
                mArrayCount++
            } catch (e: IOException) {
                throw SAXException(e)
            } catch (e: IllegalStateException) {
                throw SAXException(
                    "Error converting to JSON. nodes=" + mNodeCount + " arrays=" + mArrayCount + ". So far: '" + mWriter!!.toString() + "'",
                    e
                )
            }

            else -> Log.w(TAG, "startElement: $localName not handled")
        }
    }

    private class JsonPropertyDescription(internal var mName: String, internal var mType: String)

    companion object {
        /**
         * Maps the XML attribute to JSON.
         */
        private val XML_TO_JSON_MAP = HashMap<String, JsonPropertyDescription>()

        init {
            XML_TO_JSON_MAP["index"] = JsonPropertyDescription("__index", "int")
            XML_TO_JSON_MAP["resource-id"] = JsonPropertyDescription("resourceId", "String")
            XML_TO_JSON_MAP["content-desc"] =
                JsonPropertyDescription("contentDescription", "String")
            XML_TO_JSON_MAP["class"] = JsonPropertyDescription("clazz", "String")
            XML_TO_JSON_MAP["text"] = JsonPropertyDescription("__text", "String")
            XML_TO_JSON_MAP["package"] = JsonPropertyDescription("package", "String")
            XML_TO_JSON_MAP["checkable"] = JsonPropertyDescription("checkable", "boolean")
            XML_TO_JSON_MAP["clickable"] = JsonPropertyDescription("clickable", "boolean")
            XML_TO_JSON_MAP["enabled"] = JsonPropertyDescription("enabled", "boolean")
            XML_TO_JSON_MAP["focusable"] = JsonPropertyDescription("focusable", "boolean")
            XML_TO_JSON_MAP["scrollable"] = JsonPropertyDescription("scrollable", "boolean")
            XML_TO_JSON_MAP["long-clickable"] = JsonPropertyDescription("longClickable", "boolean")
            XML_TO_JSON_MAP["password"] = JsonPropertyDescription("password", "boolean")
            XML_TO_JSON_MAP["selected"] = JsonPropertyDescription("selected", "boolean")
        }
    }
}

/**
 * Created by diego on 2015-11-05.
 */
class ConvertDumpToJsonException(e: Throwable) : Exception(e)
