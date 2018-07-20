package au.gov.dxa

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import java.util.*
import java.net.*
import khttp.*

@Controller
class Controller {

    @Autowired
    lateinit var serviceDescriptionService:ServiceDescriptionService

    @RequestMapping("/mvp")
    fun mvp(model:MutableMap<String, Any?>): String{
        return "mvp"

    }

    @RequestMapping("/")
    fun searchSubmit(model:MutableMap<String, Any?>): String{
        model.put("services", serviceDescriptionService.list())
        return "search"

    }

    @RequestMapping("/service/{id}")
    fun detail(@PathVariable id:String, model:MutableMap<String,Any?>): String{
        return detailPage(id, "", model)
    }

    @RequestMapping("/service/{id}/{title}")
    fun detailPage(@PathVariable id:String,@PathVariable title:String, model:MutableMap<String, Any?>): String{
        val serviceDescription = serviceDescriptionService.get(id)

        if (serviceDescription == null) return "detail"
        var content:String = ""
        var pageTitle:String = title
        var pages: LinkedHashMap<String, String> = LinkedHashMap()
        var subPages: LinkedHashMap<String, MutableList<String>> = LinkedHashMap()

        var pageNum:Int = 0
        for (page in serviceDescription.pages)
        {
            var pageMarkdown = getMarkdown(resolveDefCatalogueLinks(page))
            var pageTitle = getPageTitleFromMarkdown(page)
            var subPagesList = getPageSubHeadings(page)
            pages.put(pageTitle, pageMarkdown)
            subPages.put(pageTitle, subPagesList)
            pageNum++
        }

        for (page in pages)
        {
            page.value
        }

        if (pageTitle=="" || !pages.keys.contains(pageTitle))
        {
            pageTitle = pages.keys.first()
        }

        content = pages.getValue(pageTitle)

        val lastPage = pageTitle == pages.keys.last()
        val firstPage = pageTitle == pages.keys.first()
        val currPageInded = pages.keys.indexOf(pageTitle)

        if (!lastPage) {
            model.put("nextPage", pages.keys.elementAt(currPageInded+1))
        }

        if (!firstPage) {
            model.put("prevPage", pages.keys.elementAt(currPageInded-1))
        }

        model.put("currentPage", pageTitle)
        model.put("id", id)
        model.put("model", serviceDescription)
        model.put("pageList", pages)
        model.put("subPageList", subPages)
        model.put("content", content)

        return "detail"
    }

    private fun getMarkdown(md:String):String{
        val options = MutableDataSet()
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(),StrikethroughExtension.create()));
        options.set(HtmlRenderer.GENERATE_HEADER_ID, true)
        options.set(HtmlRenderer.RENDER_HEADER_ID, true)

        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()

        // You can re-use parser and renderer instances
        val document = parser.parse(md.trimMargin())
        val html = renderer.render(document)  // "<p>This is <em>Sparta</em></p>\n"
        return html
    }

    private fun getPageTitleFromMarkdown(md:String):String {
        var splitLines = md.lines()
        var title = splitLines.find { it.startsWith("# ",true) }
        return title!!.replace("# ","") ?: "No title defined"
    }

    private fun getPageSubHeadings(md:String):MutableList<String> {
        val list = mutableListOf<String>()
        var splitLines = md.lines()
        var headings = splitLines.filter { it.startsWith("## ",true) }
        headings.forEach { it -> list.add(it.replace("## ","")) }
        return list
    }

    private fun getDiff(md:String, page:Int):String{
        //This code is not operational
        var output:String = md
        try
        {
            val response : khttp.responses.Response =khttp.post(
                    url = "http://localhost:5060/api/Diff/",
                    params = mapOf("page" to page.toString()),
                    json = mapOf("value" to md),
                    timeout = 5.00)
            output = response.text
        } catch (e:SocketTimeoutException)
        {
            output = md
        }
        return output
    }

    private fun resolveDefCatalogueLinks(md:String):String{
        var output = md
        val regex = kotlin.text.Regex("defcat```([^`\\n\\r])+```")
        var foundLinks = regex.findAll(md)
        for (item:MatchResult in  foundLinks)
        {
            var apiLinkEndpoint = item.value
            output = output.replace(apiLinkEndpoint, getDefCatalogueItem(apiLinkEndpoint))
        }

        return output
    }
    data class RootObj (val content:Definition)
    data class Definition(val name: String = "", val domain: String ="", val status: String ="", val definition: String ="", val guidance: String ="", val identifier: String ="", val usage: Array<String> = emptyArray<String>(), val type: String ="", val values: Array<String> = emptyArray<String>(), val facets: Map<String, String> = emptyMap<String, String>(), val domainAcronym: String = "", val sourceURL:String = "")
    private fun getDefCatalogueItem(linkEndpoint:String):String{
        var output:String = linkEndpoint.replace("defcat```","",true).replace("```","")
        try
        {
            val response = get("https://definitions.ausdx.io/api/definition/$output", timeout = 2.0)
            val json = response.text
            var def = Klaxon().parse<RootObj>(json)
            output = "["+def!!.content.name+"](${response.url.replace("api/","")})"
        } catch (e:Exception)
        {
            output = "```"+output+"```"
        }
        return output;
    }
}
