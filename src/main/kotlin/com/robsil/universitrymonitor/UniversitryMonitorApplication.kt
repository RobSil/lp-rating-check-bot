package com.robsil.universitrymonitor

import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.telegramBot
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component

@SpringBootApplication
class UniversitryMonitorApplication

fun main(args: Array<String>) {
    runApplication<UniversitryMonitorApplication>(*args)
}

@Component
class BotInitializer : CommandLineRunner {
    override fun run(vararg argz: String?) {

        val bot = telegramBot("6435644157:AAERr6_5UHle5dfp37HFi57GsNZpI39fxAE")
        runBlocking {
            bot.buildBehaviourWithLongPolling {
                println(getMe())
                onCommand("start") {
                    reply(it, "To check your rating, use next command! \n" +
                            "/check name url_lp name - spaces should be replaced with dots\n" +
                            "0 - is contract, other is it's priorities")
                }
                onCommandWithArgs("check") { it, args ->
                    assert(args.size == 2)
                    val name = args[0].replace(".", " ")
                    val url = args[1]
                    val response = scrape(name, url)
                    reply(
                        it,
                        "Index: ${response.index}, priorities: ${response.priorityMap}, 3,4,5 pr: ${response.priorityMap[3]!! + response.priorityMap[4]!! + response.priorityMap[5]!!}, avg rating above: ${response.avgRatingAbove}"
                    )

                }
            }.join()
        }
    }

}

data class ScrapeResponse(
    val index: Int,
    val priorityMap: Map<Int, Int>,
    val avgRatingAbove: Double
)

suspend fun scrape(name: String, url: String): ScrapeResponse {
    val doc = Jsoup.connect(url).get()
    val tableRows = doc.select("table")[0].children()[1].children()
    val priorityMap = mutableMapOf<Int, Int>()
    priorityMap.put(1, 0)
    priorityMap.put(2, 0)
    priorityMap.put(3, 0)
    priorityMap.put(4, 0)
    priorityMap.put(5, 0)
    priorityMap.put(0, 0)

    var totalRating = 0.000
    var rowIndex = 1
    for ((index, row) in tableRows.withIndex()) {
        if (row.children()[1].childNodes()[0].toString() == name) {
            println("found match! index: ${index + 1}")
            rowIndex = index + 1
            break
        }
        val priority = row.children()[2].childNodes().toString()[1].toString()
        if (priority.startsWith("к")) {
            priorityMap.compute(0) { _, value ->
                return@compute value!! + 1
            }
        } else {
            priorityMap.compute(priority.toInt()) { _, value ->
                return@compute value!! + 1
            }
        }
        totalRating += row.children()[3].childNodes().toString().slice(IntRange(1, 7)).toFloat()
    }

    println(priorityMap)
    println("avg rating above: ${totalRating / rowIndex}")
    println("sum of 3,4,5 pr: ${priorityMap[3]!! + priorityMap[4]!! + priorityMap[5]!!}")
    return ScrapeResponse(
        index = rowIndex,
        priorityMap = priorityMap.toMap(),
        avgRatingAbove = totalRating / rowIndex
    )
}
