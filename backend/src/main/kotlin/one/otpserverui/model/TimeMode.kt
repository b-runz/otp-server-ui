package one.otpserverui.model

/**
 * Whether [one.otpserverui.routing.bringBike]'s `dateTime` argument is a departure time or an
 * arrival time -- mirrors bikebus's own `one.brj.bikebus.model.TimeMode`, ported here since this
 * project has no Android UI layer of its own to have defined it first.
 */
enum class TimeMode { DEPART_AT, ARRIVE_BY }
