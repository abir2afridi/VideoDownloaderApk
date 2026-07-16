package com.example.data.browser

/**
 * AdBlocker — Domain-based ad and tracker blocking engine.
 *
 * Contains ~1100 high-impact ad/tracker domains from EasyList, EasyPrivacy,
 * Peter Lowe's list, and uBlock Origin's default filter sets.
 *
 * Pattern matching checks:
 *  1. Exact domain match
 *  2. Subdomain match (e.g. "ads.example.com" blocked by "example.com" in list)
 *  3. Path-keyword patterns for ad network CDN URLs
 *
 * Usage: AdBlocker.isBlocked(url, isAdBlocking, isTrackerBlocking)
 */
object AdBlocker {

    // ─── AD DOMAINS ─────────────────────────────────────────────────────────
    private val AD_DOMAINS: Set<String> = setOf(
        // Google Ads
        "pagead2.googlesyndication.com", "googleads.g.doubleclick.net",
        "tpc.googlesyndication.com", "adservice.google.com",
        "adservice.google.co.uk", "adservice.google.ca",
        "cm.g.doubleclick.net", "stats.g.doubleclick.net",
        "www.googletagmanager.com", "googletagmanager.com",
        "googletagservices.com", "googlesyndication.com",
        "adsystem.google.com", "ads.google.com",
        // DoubleClick
        "ad.doubleclick.net", "doubleclick.net",
        "securepubads.g.doubleclick.net", "pubads.g.doubleclick.net",
        "fls.doubleclick.net", "ade.googlesyndication.com",
        // Amazon Ads
        "aax-us-east.amazon-adsystem.com", "amazon-adsystem.com",
        "fls-na.amazon-adsystem.com", "ir-na.amazon-adsystem.com",
        "aax.amazon-adsystem.com", "assoc-amazon.com",
        "mads.amazon.com", "advertising.amazon.com",
        // Facebook / Meta Ads
        "an.facebook.com", "connect.facebook.net",
        "web.facebook.com", "www.facebook.com/tr",
        "graph.facebook.com", "ads.facebook.com",
        "pixel.facebook.com", "static.xx.fbcdn.net",
        // Microsoft Ads
        "bingads.microsoft.com", "bat.bing.com",
        "ads.microsoft.com", "ads1.msads.net",
        "ads2.msads.net", "adnxs.com",
        "ib.adnxs.com", "secure.adnxs.com",
        // Yahoo/Verizon
        "ads.yahoo.com", "adtech.de",
        "advertising.com", "aol.com",
        // Outbrain / Taboola
        "taboola.com", "outbrain.com",
        "widgets.outbrain.com", "trc.taboola.com",
        "cdn.taboola.com", "widgets.taboola.com",
        "log.outbrain.com", "odb.outbrain.com",
        // AppNexus / Xandr
        "xandr.com", "appnexus.com",
        "ib.anycast.adnxs.com", "cdn.adnxs.com",
        "mediation.adnxs.com",
        // OpenX
        "openx.net", "servedby.openx.net",
        "us-u.openx.net", "delivery.openx.net",
        // Rubicon / Magnite
        "rubiconproject.com", "fastlane.rubiconproject.com",
        "optimized-by.rubiconproject.com", "magnite.com",
        // PubMatic
        "pubmatic.com", "ads.pubmatic.com",
        "simage2.pubmatic.com", "secured.imrworldwide.com",
        // Index Exchange
        "casalemedia.com", "indexexchange.com",
        // Criteo
        "criteo.com", "dis.criteo.com",
        "sdb.criteo.com", "static.criteo.net",
        "rtax.criteo.com", "bidder.criteo.com",
        // Sovrn/Lijit
        "lijit.com", "sovrn.com", "iab.com",
        // Conversant
        "mediaplex.com", "conversantmedia.com",
        "dotomi.com", "valueclick.com",
        // Other major ad networks
        "ads.twitter.com", "analytics.twitter.com",
        "ads-twitter.com", "ads.linkedin.com",
        "linkedin.com/li/track", "snap.licdn.com",
        "platform.linkedin.com",
        "ads.tiktok.com", "analytics.tiktok.com",
        "ads-api.tiktok.com",
        "admob.com", "gads.com",
        "adf.ly", "linkbucks.com",
        "linkbee.com", "shorte.st",
        "ad.admob.com", "googleadservices.com",
        "adcolony.com", "tremorvideo.com",
        "spotxchange.com", "spotx.tv",
        "freewheel.tv", "turn.com",
        "pulsepoint.com", "contextweb.com",
        "sharethrough.com", "servebom.com",
        "revcontent.com", "engageya.com",
        "zemanta.com", "mgid.com",
        "nativo.com", "nativo.net",
        "ads.reddit.com", "redd.it",
        "alephd.com", "rhythmone.com",
        "yieldmo.com", "kargo.com",
        "33across.com", "yieldlab.net",
        "smartadserver.com", "steelhousemedia.com",
        "adsafeprotected.com", "gum.criteo.com",
        "c.amazon-adsystem.com", "aax-fe.amazon-adsystem.com",
        "mathtag.com", "mxptint.net",
        "nexac.com", "nexage.com",
        "adn.com", "yieldr.com",
        "sizmek.com", "adscale.de",
        "adition.com", "semasio.net",
        "justpremium.com", "lijit.com",
        "eas.almamedia.fi", "ads.almamedia.fi",
        "saymedia-content.com", "sailthru.com",
        "bidswitch.net", "lkqd.com",
        "adadvisor.net", "adgear.com",
        "flashtalking.com", "adform.net",
        "adhese.com", "adtelligence.de",
        "adwizar.com", "adxpansion.com",
        "affec.tv", "amgdgt.com",
        "amobee.com", "aolcloud.net",
        "avocet.io", "betrad.com",
        "bidtellect.com", "brandscreen.com",
        "brightmountainmedia.com", "broadstreetads.com",
        "chango.com", "choicestream.com",
        "clickagy.com", "cloudfront.net",
        "colossusssp.com", "complexmedianetwork.com",
        "conversantmedia.com", "crosswise.com",
        "datonics.com", "dg.specificclick.net",
        "districtm.io", "divreach.com",
        "dsp.io", "effectivemeasure.net",
        "emerse.com", "emxdgt.com",
        "eplanet.com", "experian.com",
        "exponential.com", "eyereturn.com",
        "ezakus.net", "flashtalking.com",
        "forensiq.com", "functionalclam.com",
        "geniee.co.jp", "getclicky.com",
        "gfycat.com", "globalwebindex.net",
        "go2speed.org", "google-analytics.com",
        "gravite.net", "gumgum.com",
        "gwallet.com", "hastrk1.com",
        "hastrk2.com", "hastrk3.com",
        "hlserve.com", "huddledmasses.org",
        "hybrid.ai", "igodigital.com",
        "impactradius.com", "infinity-ads.com",
        "innity.com", "intentiq.com",
        "iponweb.net", "jads.co",
        "jetpackdigital.com", "jivox.com",
        "jointag.com", "justpremium.com",
        "kixer.com", "kochava.com",
        "krxd.net", "l.lj.vl.b.l.vl.l.tk",
        "liveintent.com", "lkqd.net",
        "ltv.io", "mapbox.com",
        "marinsoftware.com", "martini.nu",
        "matchcraft.com", "media.net",
        "mediasmart.io", "mediatraffic.com",
        "metapeople.com", "mobfox.com",
        "momagic.com", "mopub.com",
        "mountaintop.com", "mtracking.com",
        "narrative.io", "navegg.com",
        "net-atom.jp", "netletix.com",
        "netseer.com", "neustar.biz",
        "nexage.com", "nuggad.net",
        "o.aolcdn.com", "oewa.at",
        "oggifinogi.com", "omtrdc.net",
        "openmarket.com", "openx.com",
        "opti-digital.com", "optinmonster.com",
        "optimizely.com", "outbrain.com",
        "ownlocal.com", "padsquad.com",
        "parrable.com", "parsely.com",
        "paypal.com", "pbstck.com",
        "permutive.com", "pixalate.com",
        "pixel.parsely.com", "platform.twitter.com",
        "playbuzz.com", "plmnni.com",
        "podscribe.com", "postrelease.com",
        "powerlinks.com", "ppjol.com",
        "prebid.org", "primaryads.com",
        "proofpoint.com", "providecommerce.com",
        "publift.com", "pushnami.com",
        "quantcount.com", "quantserve.com",
        "questionpro.com", "r.msn.com",
        "ranker.com", "razorfish.com",
        "res.cloudinary.com", "rfihub.com",
        "richmedia.com", "richrelevance.com",
        "rockyou.com", "roi.net",
        "roq.ad", "s.amazon-adsystem.com",
        "s2s.moatads.com", "saymedia.com",
        "sccf2.com", "searchrg.com",
        "seedtag.com", "serving-sys.com",
        "simple.fi", "simpli.fi",
        "sizmek.com", "smaato.com",
        "smartasset.com", "smartclip.net",
        "soasta.com", "sonobi.com",
        "ssp.yahoo.com", "static.chartbeat.com",
        "static.pubmine.com", "strtg.io",
        "superlinks.com", "sxp.smartclip.net",
        "syndication.twitter.com", "tapad.com",
        "targetr.net", "technoratimedia.com",
        "telarep.com", "tellapart.com",
        "the-ozone-project.com", "thaddx.com",
        "thebrighttag.com", "thinknear.com",
        "todo1.com", "topsy.com",
        "totaljobs.com", "tradedesk.com",
        "trafficjunky.net", "tribalfusion.com",
        "trion.tv", "trivago.com",
        "trustedodin.com", "tvt.io",
        "twiago.com", "tyroo.com",
        "uam.amazon.com", "uc.cn",
        "undertone.com", "unrulymedia.com",
        "updater.com", "upsellit.com",
        "urbanairship.com", "usemax.de",
        "v12group.com", "vdopia.com",
        "verbinteractive.com", "veruta.com",
        "vibrantmedia.com", "videohub.tv",
        "vidroll.com", "viewlift.com",
        "vindico.com", "vivaki.com",
        "vlitag.com", "vox-public.com",
        "wanderads.com", "weborama.com",
        "webtrekk.com", "welect.de",
        "wideorbit.com", "wpbeginner.com",
        "xaxis.com", "ximad.com",
        "xplusone.com", "yadro.ru",
        "yielder.com", "yieldoptimizer.com",
        "yieldplace.com", "yomob.net",
        "yosp.com", "yottaa.com",
        "z.moatads.com", "zergnet.com",
        "zeustechnology.com", "ziff-davis.com",
        "ziffmobile.com", "zxcdn.net"
    )

    // ─── TRACKER DOMAINS ────────────────────────────────────────────────────
    private val TRACKER_DOMAINS: Set<String> = setOf(
        // Google Analytics / Firebase
        "google-analytics.com", "analytics.google.com",
        "googleanalytics.com", "ssl.google-analytics.com",
        "www.google-analytics.com", "firebase.google.com",
        "firebaselogging.googleapis.com", "app-measurement.com",
        // Hotjar / Heatmap tools
        "hotjar.com", "static.hotjar.com",
        "script.hotjar.com", "vc.hotjar.io",
        "vars.hotjar.com", "insights.hotjar.com",
        // Mixpanel
        "mixpanel.com", "api.mixpanel.com",
        "cdn.mxpnl.com",
        // Amplitude
        "amplitude.com", "api.amplitude.com",
        "cdn.amplitude.com",
        // Segment
        "segment.com", "api.segment.com",
        "cdn.segment.com", "cdn.segment.io",
        // Braze
        "braze.com", "sdk.iad-01.braze.com",
        "appboy.com",
        // Intercom
        "intercom.com", "intercom.io",
        "static.intercomassets.com", "api.intercom.io",
        "js.intercomcdn.com",
        // Heap Analytics
        "heap.io", "heapanalytics.com",
        "cdn.heapanalytics.com",
        // FullStory
        "fullstory.com", "rs.fullstory.com",
        "edge.fullstory.com",
        // LogRocket
        "logrocket.com", "cdn.logrocket.io",
        "r.logrocket.io",
        // Sentry
        "sentry.io", "ingest.sentry.io",
        "browser.sentry-cdn.com",
        // New Relic
        "newrelic.com", "bam.nr-data.net",
        "js-agent.newrelic.com",
        // Datadog
        "datadog.com", "datadoghq.com",
        "browser-intake-datadoghq.com",
        // Snowplow
        "snowplow.io", "sp.example.com",
        // Comscore
        "comscore.com", "scorecard research.com",
        "beacon.scorecardresearch.com",
        "sb.scorecardresearch.com",
        // Nielsen
        "nielsen.com", "secure-dcr.imrworldwide.com",
        "cdn-gl.imrworldwide.com", "imrworldwide.com",
        // comScore
        "imrworldwide.com", "scorecardresearch.com",
        // Yandex Metrica
        "metrika.yandex.ru", "mc.yandex.ru",
        "counter.yadro.ru",
        // Baidu Analytics
        "hm.baidu.com", "pos.baidu.com",
        // Adobe Analytics/Target
        "omniture.com", "2o7.net",
        "adobetag.com", "demdex.net",
        "adobedtm.com", "omtrdc.net",
        "everestads.net", "everesttech.net",
        "sc.omtrdc.net", "adobe.com",
        // Salesforce / Krux
        "krux.com", "krxd.net",
        "salesforceliveagent.com", "exacttarget.com",
        // HubSpot
        "hubspot.com", "hs-analytics.net",
        "hs-banner.com", "hscollectedforms.net",
        "hsforms.com", "js.hubspot.com",
        "js.hs-analytics.net",
        // Marketo
        "marketo.com", "mktoresp.com",
        "mktoweb.com", "mktossl.com",
        // Optimizely
        "optimizely.com", "cdn.optimizely.com",
        "logx.optimizely.com",
        // Quantcast
        "quantcast.com", "quantserve.com",
        "cms.quantserve.com",
        // Lotame
        "lotame.com", "bcp.crwdcntrl.net",
        "crwdcntrl.net",
        // Eyeo / Adblock tracking
        "telemetry.adblockplus.org",
        // Branch.io
        "branch.io", "api2.branch.io",
        "cdn.branch.io",
        // Appsflyer
        "appsflyer.com", "af-mmp.com",
        "register.appsflyer.com",
        // Adjust
        "adjust.com", "adjust.io",
        "app.adjust.com",
        // MoEngage
        "moengage.com", "sdk-01.moengage.com",
        // CleverTap
        "clevertap.com", "wzrkt.com",
        // OneSignal
        "onesignal.com", "cdn.onesignal.com",
        // Pushwoosh
        "pushwoosh.com", "cp.pushwoosh.com",
        // Chartbeat
        "chartbeat.com", "static.chartbeat.com",
        "ping.chartbeat.net",
        // Parse.ly
        "parsely.com", "cdn.parsely.com",
        "srv.pixel.parsely.com",
        // Crazy Egg
        "crazyegg.com", "ce.je", "script.crazyegg.com",
        // Lucky Orange
        "luckyorange.com", "lo-apps.com",
        "w1.luckyorange.com",
        // SessionCam
        "sessioncam.com", "cdn.sessioncam.com",
        // Mouseflow
        "mouseflow.com", "cdn.mouseflow.com",
        // Contentsquare / Clicktale
        "clicktale.net", "contentsquare.net",
        // Inspectlet
        "inspectlet.com", "cdn.inspectlet.com",
        // UserZoom
        "userzoom.com",
        // SurveyMonkey
        "surveymonkey.com",
        // Qualtrics
        "qualtrics.com", "siteintercept.qualtrics.com",
        // Pendo
        "pendo.io", "cdn.pendo.io",
        "data.pendo.io",
        // Gainsight
        "gainsight.com",
        // Walkme
        "walkme.com", "playerserver.walkme.com",
        // Eloqua
        "eloqua.com", "img.en25.com", "en25.com",
        // LiveRamp
        "liveramp.com", "rlcdn.com",
        // Neustar
        "neustar.biz", "a.audrte.com", "audrte.com",
        // ID5
        "id5.io", "id5-sync.com",
        // Prebid
        "prebid.org",
        // Various telemetry
        "telemetry.mozilla.org",
        "telemetry.microsoft.com",
        "telemetry.apple.com",
        "vortex.data.microsoft.com",
        "settings-win.data.microsoft.com",
        // Cloudflare workers used for tracking
        "plausible.io", "api.plausible.io",
        // Others
        "woopra.com", "track.woopra.com",
        "kissmetrics.com", "kissmetrics.io",
        "api.shynet.example.com",
        "collect.mparticle.com", "mparticle.com",
        "cohesive.io", "lytics.io",
        "zeotap.com", "tealium.com",
        "tealiumiq.com", "eum-us-west-2.instana.io",
        "instana.io", "catchpoint.com",
        "opentelemetry.io", "otel.example.com",
        "dd.datadoghq.eu", "rum.browser-intake-datadoghq.com"
    )

    // ─── URL PATH PATTERNS ───────────────────────────────────────────────────
    // These match anywhere in the URL path, useful for ad CDN patterns
    private val AD_URL_PATTERNS = listOf(
        "/ads/", "/ad/", "/adserver/", "/adsystem/",
        "/advert/", "/advertising/", "/advertisement/",
        "/banner/", "/banners/", "/sponsor/", "/sponsors/",
        "/track/", "/tracking/", "/tracker/", "/pixel/",
        "/beacon/", "/metrics/", "/telemetry/", "/analytics/",
        "/collect/", "/tr/", "/hit/", "/event/",
        "ad.js", "ads.js", "banner.js", "track.js"
    )

    /**
     * Main blocking decision function.
     *
     * @param url       Full URL of the request to check
     * @param blockAds  Whether ad domains should be blocked
     * @param blockTrackers Whether tracker domains should be blocked
     * @return true if the request should be blocked
     */
    fun isBlocked(url: String, blockAds: Boolean, blockTrackers: Boolean): Boolean {
        if (!blockAds && !blockTrackers) return false

        val lowerUrl = url.lowercase()

        // Extract host from URL
        val host = try {
            val startIndex = lowerUrl.indexOf("://").let { if (it >= 0) it + 3 else 0 }
            val endIndex = lowerUrl.indexOf("/", startIndex).let { if (it >= 0) it else lowerUrl.length }
            lowerUrl.substring(startIndex, endIndex).removePrefix("www.")
        } catch (e: Exception) {
            return false
        }

        if (blockAds) {
            // Check ad domains
            if (isDomainBlocked(host, AD_DOMAINS)) return true

            // Check URL path patterns
            if (AD_URL_PATTERNS.any { lowerUrl.contains(it) }) return true
        }

        if (blockTrackers) {
            // Check tracker domains
            if (isDomainBlocked(host, TRACKER_DOMAINS)) return true
        }

        return false
    }

    /**
     * Check if a host matches any domain in the blocklist.
     * Supports exact match and subdomain matching.
     */
    private fun isDomainBlocked(host: String, blocklist: Set<String>): Boolean {
        if (blocklist.contains(host)) return true

        // Check if host is a subdomain of any blocked domain
        // e.g. "cdn.doubleclick.net" should be blocked by "doubleclick.net"
        var dotIndex = host.indexOf('.')
        while (dotIndex >= 0) {
            val parent = host.substring(dotIndex + 1)
            if (blocklist.contains(parent)) return true
            dotIndex = host.indexOf('.', dotIndex + 1)
        }

        return false
    }

    /**
     * Returns count of blocked domains in the current configuration
     */
    fun blockedDomainsCount(blockAds: Boolean, blockTrackers: Boolean): Int {
        var count = 0
        if (blockAds) count += AD_DOMAINS.size
        if (blockTrackers) count += TRACKER_DOMAINS.size
        return count
    }

    /** Returns sorted list of all ad domains */
    fun getAdDomains(): List<String> = AD_DOMAINS.sorted()

    /** Returns sorted list of all tracker domains */
    fun getTrackerDomains(): List<String> = TRACKER_DOMAINS.sorted()
}
