package de.fabihbbbt.assetbrowser.client.gui.index

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.TreeSet
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

class AssetIndex(
    private val resourceManager: ResourceManager,
) {
    private val allAssetsList: List<AssetRecord>
    private val byId: Map<Identifier, AssetRecord>
    private val packIdsList: List<String>
    private val scanThread: Thread

    @Volatile
    private var usages: Map<Identifier, List<Identifier>>? = null

    @Volatile
    private var scanCancelled = false

    init {
        val resources = HashMap<Identifier, Resource>()
        for (category in ASSET_CATEGORIES) {
            resources.putAll(resourceManager.listResources(category) { true })
        }

        val records = ArrayList<AssetRecord>(resources.size)
        val lookup = HashMap<Identifier, AssetRecord>(resources.size)
        for ((id, resource) in resources) {
            val record = AssetRecord.of(id, resource.sourcePackId())
            records.add(record)
            lookup[id] = record
        }
        records.sortWith(compareBy { it.id })

        allAssetsList = records.toList()
        byId = lookup.toMap()
        packIdsList = allAssetsList.map { it.packId }.distinct().sorted()

        scanThread = Thread(this::scanUsages, "assetbrowser-asset-scan")
        scanThread.isDaemon = true
        scanThread.start()
    }

    fun cancelScan(): CompletableFuture<Void> {
        scanCancelled = true
        if (!scanThread.isAlive) {
            return CompletableFuture.completedFuture(null)
        }
        val done = CompletableFuture<Void>()
        val waiter =
            Thread({
                try {
                    scanThread.join()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                done.complete(null)
            }, "assetbrowser-asset-scan-cancel-wait")
        waiter.isDaemon = true
        waiter.start()
        return done
    }

    fun all(): List<AssetRecord> = allAssetsList

    fun packIds(): List<String> = packIdsList

    fun recordOf(id: Identifier): AssetRecord? = byId[id]

    fun isScanning(): Boolean = usages == null

    fun usagesOf(id: Identifier): List<Identifier> = usages?.get(id) ?: emptyList()

    fun referencesIn(
        self: Identifier,
        content: String,
    ): List<Identifier> {
        val found = LinkedHashSet<Identifier>()
        collectTokens(content) { token ->
            for (candidate in expand(token)) {
                if (candidate != self && byId.containsKey(candidate)) {
                    found.add(candidate)
                }
            }
        }
        return found.sorted()
    }

    private fun scanUsages() {
        val found = HashMap<Identifier, MutableList<Identifier>>()
        for ((id) in allAssetsList) {
            if (scanCancelled) {
                return
            }
            if (!isTextAsset(id.path)) {
                continue
            }
            val content = readString(id) ?: continue
            for (target in referencesIn(id, content)) {
                found.getOrPut(target) { ArrayList() }.add(id)
            }
        }
        if (scanCancelled) {
            return
        }
        found.values.forEach { it.sort() }
        usages = found
    }

    private fun readString(id: Identifier): String? =
        resourceManager
            .getResource(id)
            .orElse(null)
            ?.readBytesOrNull()
            ?.let { String(it, StandardCharsets.UTF_8) }

    companion object {
        private val ASSET_CATEGORIES =
            listOf(
                "atlases",
                "blockstates",
                "equipment",
                "font",
                "items",
                "lang",
                "models",
                "particles",
                "post_effect",
                "shaders",
                "sounds",
                "texts",
                "textures",
                "waypoint_style",
            )

        private val TEXT_EXTENSIONS =
            listOf(
                ".json",
                ".mcmeta",
                ".txt",
                ".fsh",
                ".vsh",
                ".glsl",
                ".properties",
                ".lang",
                ".csv",
                ".tsv",
                ".md",
            )

        fun isTextAsset(path: String): Boolean = TEXT_EXTENSIONS.any { path.endsWith(it) }

        private val QUOTED_TOKEN: Pattern = Pattern.compile("\"([a-z0-9_.:/-]{3,})\"")
        private val MOJ_IMPORT: Pattern = Pattern.compile("#moj_import\\s+<([a-z0-9_.:/-]+)>")
        private val NUMBERED_FILE: Pattern = Pattern.compile("(.+)_(\\d+)((?:\\.[A-Za-z0-9]+)+)")

        private fun collectTokens(
            content: String,
            output: (String) -> Unit,
        ) {
            val quoted = QUOTED_TOKEN.matcher(content)
            while (quoted.find()) {
                val token = quoted.group(1)
                if (token.indexOf('/') >= 0 || token.indexOf(':') >= 0) {
                    output(token)
                }
            }
            val imports = MOJ_IMPORT.matcher(content)
            while (imports.find()) {
                output(imports.group(1))
            }
        }

        fun expand(token: String): List<Identifier> {
            val namespace = token.substringBefore(':', "minecraft")
            val path = token.substringAfter(':')
            return listOf(
                path,
                "textures/$path.png",
                "textures/$path",
                "textures/particle/$path.png",
                "textures/particle/$path",
                "models/$path.json",
                "$path.json",
                "font/$path",
                "shaders/$path",
                "shaders/$path.vsh",
                "shaders/$path.fsh",
                "shaders/include/$path",
            ).mapNotNull { Identifier.tryBuild(namespace, it) }
        }

        const val NO_EXTENSION = "(no extension)"

        fun extensionOf(path: String): String {
            val fileName = path.substringAfterLast('/')
            val dot = fileName.lastIndexOf('.')
            return if (dot >= 0) fileName.substring(dot) else NO_EXTENSION
        }

        fun summarizeBlockstate(raw: String): String? {
            val root = runCatching { JsonParser.parseString(raw) }.getOrNull() ?: return null
            return summarizeBlockstate(root)
        }

        fun summarizeBlockstate(root: JsonElement): String? {
            if (!root.isJsonObject) {
                return null
            }
            val obj = root.asJsonObject
            if (obj.has("variants") && obj.get("variants").isJsonObject) {
                val variants = obj.getAsJsonObject("variants")
                val properties = TreeSet<String>()
                for (key in variants.keySet()) {
                    for (clause in key.split(",")) {
                        if (clause.isNotEmpty()) {
                            val eq = clause.indexOf('=')
                            properties.add(if (eq >= 0) clause.substring(0, eq) else clause)
                        }
                    }
                }
                var summary = "${variants.size()} variant${if (variants.size() == 1) "" else "s"}"
                if (properties.isNotEmpty()) {
                    summary += " · " + properties.joinToString(", ")
                }
                return summary
            }
            if (obj.has("multipart") && obj.get("multipart").isJsonArray) {
                val rules = obj.getAsJsonArray("multipart").size()
                return "multipart, $rules rule${if (rules == 1) "" else "s"}"
            }
            return null
        }

        fun buildTree(records: List<AssetRecord>): Node {
            val root = Node("", -1)
            for (record in records) {
                var depth = 0
                var current = root.child(record.id.namespace, depth++)
                for (segment in record.id.path.split("/")) {
                    current = current.child(segment, depth++)
                }
                current.record = record
            }
            groupNumberedSiblings(root)
            sortRecursively(root)
            for (namespace in root.sortedChildren) {
                namespace.expanded = true
            }
            return root
        }

        private fun groupNumberedSiblings(node: Node) {
            if (node.record != null) {
                return
            }
            val subfolders = ArrayList<Node>()
            val families = LinkedHashMap<String, MutableList<NumberedFile>>()
            for (child in node.childrenByName.values) {
                if (child.record == null) {
                    subfolders.add(child)
                    continue
                }
                val matcher = NUMBERED_FILE.matcher(child.name)
                if (matcher.matches()) {
                    val file = NumberedFile(child, matcher.group(1), matcher.group(2), matcher.group(3))
                    families.getOrPut("${file.base}/${file.suffix}") { ArrayList() }.add(file)
                }
            }
            for (members in families.values) {
                if (members.size < 2) {
                    continue
                }
                for (member in members) {
                    node.childrenByName.remove(member.node.name)
                }
                val group = buildGroupNode(members)
                node.childrenByName[group.name] = group
            }
            for (subfolder in subfolders) {
                groupNumberedSiblings(subfolder)
            }
        }

        private class NumberedFile(
            val node: Node,
            val base: String,
            val digits: String,
            val suffix: String,
        )

        private fun buildGroupNode(members: List<NumberedFile>): Node {
            val first = members[0]
            val depth = first.node.depth
            val indices = members.map { it.digits.toInt() }
            val label = "${first.base}_${indices.min()}-${indices.max()}${first.suffix} (${members.size})"
            val group = Node(label, depth)
            group.isNumberedGroup = true
            for (member in members) {
                member.node.depth = depth + 1
                group.childrenByName[member.node.name] = member.node
            }
            return group
        }

        private fun findChild(
            parent: Node,
            segment: String,
        ): Node? {
            val direct = parent.childrenByName[segment]
            if (direct != null) {
                return direct
            }
            for (child in parent.childrenByName.values) {
                if (child.isNumberedGroup) {
                    val nested = child.childrenByName[segment]
                    if (nested != null) {
                        child.expanded = true
                        return nested
                    }
                }
            }
            return null
        }

        private fun sortRecursively(node: Node) {
            val children = ArrayList(node.childrenByName.values)
            children.sortWith(compareBy<Node> { if (it.isFile()) 1 else 0 }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            node.sortedChildren = children.toList()
            children.forEach { sortRecursively(it) }
        }

        fun expandTo(
            root: Node,
            id: Identifier,
        ) {
            var current = root.childrenByName[id.namespace] ?: return
            for (segment in id.path.split("/")) {
                current.expanded = true
                current = findChild(current, segment) ?: return
            }
        }
    }

    data class AssetRecord(
        val id: Identifier,
        val packId: String,
        val searchKey: String,
    ) {
        companion object {
            fun of(
                id: Identifier,
                packId: String,
            ): AssetRecord = AssetRecord(id, packId, "$id $packId".lowercase(Locale.ROOT))
        }
    }

    class Node(
        val name: String,
        var depth: Int,
    ) {
        val childrenByName: MutableMap<String, Node> = HashMap()

        var sortedChildren: List<Node> = emptyList()
        var expanded: Boolean = false
        var record: AssetRecord? = null

        var isNumberedGroup: Boolean = false

        fun child(
            name: String,
            depth: Int,
        ): Node = childrenByName.getOrPut(name) { Node(name, depth) }

        fun isFile(): Boolean = record != null && childrenByName.isEmpty()

        fun groupMembers(): List<AssetRecord> {
            if (!isNumberedGroup) {
                return emptyList()
            }
            return sortedChildren.mapNotNull { it.record }
        }
    }
}

internal fun Resource.readBytesOrNull(): ByteArray? = runCatching { open().use { it.readAllBytes() } }.getOrNull()
