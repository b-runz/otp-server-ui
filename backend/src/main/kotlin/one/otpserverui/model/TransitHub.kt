package one.otpserverui.model

/**
 * A cataloged Danish transit interchange hub -- mirrors bikebus's own
 * `one.brj.bikebus.model.TransitHub`, ported here (like [TimeMode]) since this project has no
 * Android UI layer of its own to have defined it first. `stopIds` are already feed-qualified (e.g.
 * "1:000739000101") to match OTP's own gtfsId format. Backs
 * [one.otpserverui.routing.HubRouting]'s "prefer transit hubs" trip-splitting logic and is loaded
 * from the bundled `hubs.json` classpath resource by [one.otpserverui.routing.HubCatalog].
 */
data class TransitHub(val name: String, val lat: Double, val lon: Double, val stopIds: List<String>)
