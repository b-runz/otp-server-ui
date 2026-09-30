# VENDORED.md - :otp-routing

## 1. Upstream
- URL: https://github.com/opentripplanner/OpenTripPlanner
- Commit: 61a3af6798 (v2.9.0-2454-g61a3af6798)
- Description: Vendored copy of the "main" routing slice of OpenTripPlanner's `application`
  module: everything under `application/src/main/java` needed to compile
  `routing.algorithm.RoutingWorker` and its transitive dependencies, excluding
  `standalone.*`, `updater.*`, `graph_builder.*`, `ext.*`, `apis.*`, `inspector.*`,
  `visualizer.*`, `netex.*`, `gtfs.*`, `osm.*` (those are either out of scope for the embedded
  target or stubbed at the boundary — see `STUBS.md`).
- Extra pinned dependency added for this module only: `com.fasterxml.jackson.core:jackson-annotations:2.22`
  (annotation-only, no transitive deps) for `transit/model/site/Station.java`'s
  `@JsonBackReference` field annotation (used only for snapshot-test JSON serialization). The
  upstream `pom.xml` pins `jackson.version` to `2.22.2`, but Maven Central only publishes
  `jackson-annotations` up to `2.22` (no `.2` patch release exists for this artifact) — `2.22` is
  used here as the closest available match. `jackson-core`/`jackson-databind` remain forbidden.

## 2. What was copied
`src/main/java` — verbatim copies of upstream files (byte-for-byte via `cp`), except the files
listed in section 4 below (edited) and section 3 / `STUBS.md` (stubs, never copied from upstream
bodies). Full generated list of the 590 `.java` files present in this module as of Task 4
(`find src/main/java -name '*.java' | sort`), 560 of which are verbatim copies and 30 of which are
stubs. Task 5 (porting `src/testFixtures/java` and `src/test/java`, see the addendum starting at
section 6) added 47 more files to `src/main/java` — see section 10 for that list; the module now
has 637 `.java` files under `src/main/java` in total:

```
org/opentripplanner/api/resource/DebugOutput.java
org/opentripplanner/api/resource/TransitTimingOutput.java
org/opentripplanner/ext/accessibilityscore/DecorateWithAccessibilityScore.java  [STUB]
org/opentripplanner/ext/carpooling/CarpoolingService.java  [STUB]
org/opentripplanner/ext/carpooling/internal/CarpoolItineraryMapper.java  [STUB]
org/opentripplanner/ext/carpooling/routing/CarpoolAccessEgress.java  [STUB]
org/opentripplanner/ext/dataoverlay/api/DataOverlayParameters.java  [STUB]
org/opentripplanner/ext/dataoverlay/configuration/DataOverlayParameterBindings.java  [STUB]
org/opentripplanner/ext/dataoverlay/routing/DataOverlayContext.java  [STUB]
org/opentripplanner/ext/flex/edgetype/FlexTripEdge.java  [STUB]
org/opentripplanner/ext/flex/FlexAccessEgress.java  [STUB]
org/opentripplanner/ext/flex/FlexibleTransitLeg.java  [STUB]
org/opentripplanner/ext/flex/FlexIndex.java  [STUB]
org/opentripplanner/ext/flex/FlexParameters.java  [STUB]
org/opentripplanner/ext/flex/trip/FlexTrip.java  [STUB]
org/opentripplanner/ext/realtimeresolver/RealtimeResolver.java  [STUB]
org/opentripplanner/ext/ridehailing/DecorateWithRideHailing.java  [STUB]
org/opentripplanner/ext/ridehailing/RideHailingAccessShifter.java  [STUB]
org/opentripplanner/ext/ridehailing/RideHailingService.java  [STUB]
org/opentripplanner/ext/sorlandsbanen/SorlandsbanenNorwayService.java  [STUB]
org/opentripplanner/ext/stopconsolidation/DecorateConsolidatedStopNames.java  [STUB]
org/opentripplanner/ext/stopconsolidation/StopConsolidationService.java  [STUB]
org/opentripplanner/framework/application/OtpAppException.java
org/opentripplanner/framework/application/OTPFeature.java
org/opentripplanner/framework/application/OtpFileNames.java
org/opentripplanner/framework/application/OTPRequestTimeoutException.java
org/opentripplanner/framework/error/DefaultOtpError.java
org/opentripplanner/framework/error/OtpError.java
org/opentripplanner/framework/model/Gram.java
org/opentripplanner/framework/model/TimeAndCost.java
org/opentripplanner/framework/time/ZoneIdFallback.java
org/opentripplanner/framework/token/Deserializer.java
org/opentripplanner/framework/token/FieldDefinition.java
org/opentripplanner/framework/token/Serializer.java
org/opentripplanner/framework/token/Token.java
org/opentripplanner/framework/token/TokenBuilder.java
org/opentripplanner/framework/token/TokenDefinition.java
org/opentripplanner/framework/token/TokenDefinitionBuilder.java
org/opentripplanner/framework/token/TokenFormatterConfiguration.java
org/opentripplanner/framework/token/TokenSchema.java
org/opentripplanner/framework/token/TokenType.java
org/opentripplanner/model/calendar/CalendarServiceData.java
org/opentripplanner/model/fare/FareMedium.java
org/opentripplanner/model/fare/FareOffer.java
org/opentripplanner/model/fare/FareProduct.java
org/opentripplanner/model/fare/FareProductBuilder.java
org/opentripplanner/model/fare/RiderCategory.java
org/opentripplanner/model/fare/RiderCategoryBuilder.java
org/opentripplanner/model/FeedInfo.java
org/opentripplanner/model/Frequency.java
org/opentripplanner/model/GenericLocation.java
org/opentripplanner/model/modes/AllowAllModesFilter.java
org/opentripplanner/model/modes/AllowMainAndSubModeFilter.java
org/opentripplanner/model/modes/AllowMainAndSubModesFilter.java
org/opentripplanner/model/modes/AllowMainModeFilter.java
org/opentripplanner/model/modes/AllowMainModesFilter.java
org/opentripplanner/model/modes/AllowNarrowedTransitModeFilter.java
org/opentripplanner/model/modes/AllowNarrowedTransitModesFilter.java
org/opentripplanner/model/modes/AllowTransitModeFilter.java
org/opentripplanner/model/modes/ExcludeAllTransitFilter.java
org/opentripplanner/model/modes/FilterCollection.java
org/opentripplanner/model/modes/FilterFactory.java
org/opentripplanner/model/PickDrop.java
org/opentripplanner/model/plan/AlertsAware.java
org/opentripplanner/model/plan/Emission.java
org/opentripplanner/model/plan/grouppriority/TransitGroupPriorityItineraryDecorator.java
org/opentripplanner/model/plan/ItinerariesCalculateLegTotals.java  [JDK17]
org/opentripplanner/model/plan/Itinerary.java  [JDK17]
org/opentripplanner/model/plan/ItineraryBuilder.java
org/opentripplanner/model/plan/ItinerarySortKey.java
org/opentripplanner/model/plan/Leg.java
org/opentripplanner/model/plan/leg/FrequencyTransitLeg.java
org/opentripplanner/model/plan/leg/FrequencyTransitLegBuilder.java
org/opentripplanner/model/plan/leg/LegCallTime.java
org/opentripplanner/model/plan/leg/LegRealTimeEstimate.java
org/opentripplanner/model/plan/leg/ScheduledTransitLeg.java
org/opentripplanner/model/plan/leg/ScheduledTransitLegBuilder.java
org/opentripplanner/model/plan/leg/StopArrival.java
org/opentripplanner/model/plan/leg/StopArrivalMapper.java
org/opentripplanner/model/plan/leg/StreetLeg.java
org/opentripplanner/model/plan/leg/StreetLegBuilder.java
org/opentripplanner/model/plan/leg/UnknownPathLeg.java
org/opentripplanner/model/plan/leg/ViaLocationType.java
org/opentripplanner/model/plan/legreference/LegReference.java
org/opentripplanner/model/plan/legreference/ScheduledTransitLegReference.java
org/opentripplanner/model/plan/paging/cursor/DeduplicationPageCut.java
org/opentripplanner/model/plan/paging/cursor/PageCursor.java
org/opentripplanner/model/plan/paging/cursor/PageCursorFactory.java
org/opentripplanner/model/plan/paging/cursor/PageCursorInput.java
org/opentripplanner/model/plan/paging/cursor/PageCursorSerializer.java
org/opentripplanner/model/plan/paging/cursor/PageType.java
org/opentripplanner/model/plan/paging/PagingSearchWindowAdjuster.java
org/opentripplanner/model/plan/Place.java
org/opentripplanner/model/plan/SortOrder.java
org/opentripplanner/model/plan/TransitLeg.java
org/opentripplanner/model/plan/TripPlan.java
org/opentripplanner/model/plan/VehicleParkingWithEntrance.java
org/opentripplanner/model/plan/VertexType.java
org/opentripplanner/model/plan/walkstep/AbsoluteDirection.java
org/opentripplanner/model/plan/walkstep/RelativeDirection.java
org/opentripplanner/model/plan/walkstep/verticaltransportation/ElevatorUse.java
org/opentripplanner/model/plan/walkstep/verticaltransportation/EscalatorUse.java
org/opentripplanner/model/plan/walkstep/verticaltransportation/StairsUse.java
org/opentripplanner/model/plan/walkstep/verticaltransportation/VerticalDirection.java
org/opentripplanner/model/plan/walkstep/verticaltransportation/VerticalTransportationUse.java
org/opentripplanner/model/plan/walkstep/verticaltransportation/VerticalTransportationUseFactory.java
org/opentripplanner/model/plan/walkstep/WalkStep.java
org/opentripplanner/model/plan/walkstep/WalkStepBuilder.java
org/opentripplanner/model/projectinfo/GraphFileHeader.java
org/opentripplanner/model/projectinfo/MavenProjectVersion.java
org/opentripplanner/model/projectinfo/OtpProjectInfo.java
org/opentripplanner/model/projectinfo/OtpProjectInfoParser.java
org/opentripplanner/model/projectinfo/VersionControlInfo.java
org/opentripplanner/model/StopTime.java
org/opentripplanner/model/StopTimesInPattern.java
org/opentripplanner/model/SystemNotice.java
org/opentripplanner/model/TripTimeOnDate.java
org/opentripplanner/place/api/NearbyStop.java
org/opentripplanner/place/NearbyStopFinder.java
org/opentripplanner/place/nearbystopfinder/ChronologicalGraphPath.java
org/opentripplanner/place/nearbystopfinder/NearbyStopFactory.java
org/opentripplanner/place/nearbystopfinder/NearbyStopFinderVisitor.java
org/opentripplanner/place/nearbystopfinder/StopFinderTraverseVisitor.java
org/opentripplanner/place/nearbystopfinder/StraightLineNearbyStopFinder.java
org/opentripplanner/place/nearbystopfinder/StreetNearbyStopFinder.java
org/opentripplanner/routing/alertpatch/AlertCalendar.java
org/opentripplanner/routing/alertpatch/AlertCause.java
org/opentripplanner/routing/alertpatch/AlertEffect.java
org/opentripplanner/routing/alertpatch/AlertSeverity.java
org/opentripplanner/routing/alertpatch/AlertUrl.java
org/opentripplanner/routing/alertpatch/EntityKey.java
org/opentripplanner/routing/alertpatch/EntitySelector.java
org/opentripplanner/routing/alertpatch/StopCondition.java
org/opentripplanner/routing/alertpatch/StopConditionsHelper.java
org/opentripplanner/routing/alertpatch/TransitAlert.java
org/opentripplanner/routing/alertpatch/TransitAlertBuilder.java
org/opentripplanner/routing/algorithm/filterchain/api/GroupBySimilarity.java
org/opentripplanner/routing/algorithm/filterchain/api/TransitGeneralizedCostFilterParams.java
org/opentripplanner/routing/algorithm/filterchain/filters/street/RemoveBikeRentalWithMostlyWalking.java
org/opentripplanner/routing/algorithm/filterchain/filters/street/RemoveNonTransitItinerariesBasedOnGeneralizedCost.java
org/opentripplanner/routing/algorithm/filterchain/filters/street/RemoveParkAndRideWithMostlyWalkingFilter.java
org/opentripplanner/routing/algorithm/filterchain/filters/street/RemoveWalkOnlyFilter.java
org/opentripplanner/routing/algorithm/filterchain/filters/system/FlexSearchWindowFilter.java
org/opentripplanner/routing/algorithm/filterchain/filters/system/mcmax/Group.java  [JDK17]
org/opentripplanner/routing/algorithm/filterchain/filters/system/mcmax/Item.java
org/opentripplanner/routing/algorithm/filterchain/filters/system/mcmax/McMaxLimitFilter.java
org/opentripplanner/routing/algorithm/filterchain/filters/system/mcmax/State.java  [JDK17]
org/opentripplanner/routing/algorithm/filterchain/filters/system/NumItinerariesFilter.java
org/opentripplanner/routing/algorithm/filterchain/filters/system/NumItinerariesFilterResult.java
org/opentripplanner/routing/algorithm/filterchain/filters/system/OutsideSearchWindowFilter.java
org/opentripplanner/routing/algorithm/filterchain/filters/system/PagingFilter.java
org/opentripplanner/routing/algorithm/filterchain/filters/system/SingleCriteriaComparator.java
org/opentripplanner/routing/algorithm/filterchain/filters/transit/DecorateTransitAlert.java
org/opentripplanner/routing/algorithm/filterchain/filters/transit/group/RemoveIfFirstOrLastTripIsTheSame.java
org/opentripplanner/routing/algorithm/filterchain/filters/transit/group/RemoveOtherThanSameLegsMaxGeneralizedCost.java
org/opentripplanner/routing/algorithm/filterchain/filters/transit/KeepItinerariesWithFewestTransfers.java
org/opentripplanner/routing/algorithm/filterchain/filters/transit/RemoveItinerariesWithShortStreetLeg.java
org/opentripplanner/routing/algorithm/filterchain/filters/transit/RemoveTransitIfStreetOnlyIsBetter.java
org/opentripplanner/routing/algorithm/filterchain/filters/transit/RemoveTransitIfStreetOnlyIsBetterResult.java
org/opentripplanner/routing/algorithm/filterchain/filters/transit/RemoveTransitIfWalkingIsBetter.java
org/opentripplanner/routing/algorithm/filterchain/filters/transit/TransitGeneralizedCostFilter.java
org/opentripplanner/routing/algorithm/filterchain/framework/filter/DecorateFilter.java
org/opentripplanner/routing/algorithm/filterchain/framework/filter/GroupByFilter.java
org/opentripplanner/routing/algorithm/filterchain/framework/filter/MaxLimit.java
org/opentripplanner/routing/algorithm/filterchain/framework/filter/RemoveFilter.java
org/opentripplanner/routing/algorithm/filterchain/framework/filter/SortingFilter.java
org/opentripplanner/routing/algorithm/filterchain/framework/filterchain/DeleteResultHandler.java
org/opentripplanner/routing/algorithm/filterchain/framework/filterchain/RoutingErrorsAttacher.java
org/opentripplanner/routing/algorithm/filterchain/framework/groupids/GroupByAllSameStations.java
org/opentripplanner/routing/algorithm/filterchain/framework/groupids/GroupByDistance.java
org/opentripplanner/routing/algorithm/filterchain/framework/groupids/GroupBySameFirstOrLastTrip.java
org/opentripplanner/routing/algorithm/filterchain/framework/groupids/GroupBySameRoutesAndStops.java
org/opentripplanner/routing/algorithm/filterchain/framework/sort/SortOrderComparator.java
org/opentripplanner/routing/algorithm/filterchain/framework/spi/GroupId.java
org/opentripplanner/routing/algorithm/filterchain/framework/spi/ItineraryDecorator.java
org/opentripplanner/routing/algorithm/filterchain/framework/spi/ItineraryListFilter.java
org/opentripplanner/routing/algorithm/filterchain/framework/spi/RemoveItineraryFlagger.java
org/opentripplanner/routing/algorithm/filterchain/ItineraryListFilterChain.java
org/opentripplanner/routing/algorithm/filterchain/ItineraryListFilterChainBuilder.java
org/opentripplanner/routing/algorithm/filterchain/PageCursorInputAggregator.java
org/opentripplanner/routing/algorithm/filterchain/paging/DefaultPageCursorInput.java
org/opentripplanner/routing/algorithm/mapping/AlertToLegMapper.java
org/opentripplanner/routing/algorithm/mapping/ItinerariesHelper.java
org/opentripplanner/routing/algorithm/mapping/LegsToItineraryMapper.java
org/opentripplanner/routing/algorithm/mapping/PagingServiceFactory.java
org/opentripplanner/routing/algorithm/mapping/RaptorPathToItineraryMapper.java  [JDK17]
org/opentripplanner/routing/algorithm/mapping/RouteRequestToFilterChainMapper.java
org/opentripplanner/routing/algorithm/mapping/RoutingResponseMapper.java
org/opentripplanner/routing/algorithm/mapping/StatesToWalkStepsMapper.java  [JDK17]
org/opentripplanner/routing/algorithm/mapping/StreetModeToTransferTraverseModeMapper.java
org/opentripplanner/routing/algorithm/mapping/StreetPathToLegsMapper.java  [JDK17]
org/opentripplanner/routing/algorithm/mapping/TripPlanMapper.java  [JDK17]
org/opentripplanner/routing/algorithm/mapping/ViaLocationTypeMapper.java
org/opentripplanner/routing/algorithm/raptoradapter/path/PathDiff.java
org/opentripplanner/routing/algorithm/raptoradapter/router/AccessEgressFetcher.java
org/opentripplanner/routing/algorithm/raptoradapter/router/AdditionalSearchDays.java
org/opentripplanner/routing/algorithm/raptoradapter/router/FilterTransitWhenDirectModeIsEmpty.java
org/opentripplanner/routing/algorithm/raptoradapter/router/performance/PerformanceTimersForRaptor.java  [MONITORING]
org/opentripplanner/routing/algorithm/raptoradapter/router/startonboardaccess/LocationInTripPatternReference.java
org/opentripplanner/routing/algorithm/raptoradapter/router/startonboardaccess/RoutingStartOnBoardAccess.java
org/opentripplanner/routing/algorithm/raptoradapter/router/startonboardaccess/TripAndServiceDate.java
org/opentripplanner/routing/algorithm/raptoradapter/router/startonboardaccess/TripAndServiceDateResolver.java  [JDK17]
org/opentripplanner/routing/algorithm/raptoradapter/router/startonboardaccess/TripLocationResolver.java  [JDK17]
org/opentripplanner/routing/algorithm/raptoradapter/router/startonboardaccess/TripScheduleIndexResolver.java
org/opentripplanner/routing/algorithm/raptoradapter/router/street/AccessEgresses.java
org/opentripplanner/routing/algorithm/raptoradapter/router/street/AccessEgressPenaltyDecorator.java
org/opentripplanner/routing/algorithm/raptoradapter/router/street/AccessEgressRouter.java
org/opentripplanner/routing/algorithm/raptoradapter/router/street/AccessEgressType.java
org/opentripplanner/routing/algorithm/raptoradapter/router/street/DirectFlexRouter.java  [STUB - replaced]
org/opentripplanner/routing/algorithm/raptoradapter/router/street/DirectStreetRouter.java
org/opentripplanner/routing/algorithm/raptoradapter/router/street/FlexAccessEgressRouter.java  [STUB - replaced]
org/opentripplanner/routing/algorithm/raptoradapter/router/street/GraphPathFinder.java
org/opentripplanner/routing/algorithm/raptoradapter/router/TransitRouter.java  [MONITORING]
org/opentripplanner/routing/algorithm/raptoradapter/router/TransitRouterResult.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/AccessEgressWithExtraCost.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/cost/CostCalculatorFactory.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/cost/DefaultCostCalculator.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/cost/DefaultTripSchedule.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/cost/FactorStrategy.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/cost/GeneralizedCostParameters.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/cost/GeneralizedCostParametersBuilder.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/cost/IndexBasedFactorStrategy.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/cost/PatternCostCalculator.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/cost/RaptorCostLinearFunction.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/cost/SingleValueFactorStrategy.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/cost/WheelchairCostCalculator.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/DefaultAccessEgress.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/DefaultSlackProvider.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/FlexAccessEgressAdapter.java  [STUB - replaced]
org/opentripplanner/routing/algorithm/raptoradapter/transit/frequency/FrequencyAlightEvent.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/frequency/FrequencyBoardingEvent.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/frequency/FrequencyBoardOrAlightEvent.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/frequency/TripFrequencyAlightSearch.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/frequency/TripFrequencyBoardSearch.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/mappers/AccessEgressMapper.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/mappers/DirectTransitRequestMapper.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/mappers/GeneralizedCostParametersMapper.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/mappers/LookupStopIndexCallback.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/mappers/RaptorRequestMapper.java  [MONITORING]
org/opentripplanner/routing/algorithm/raptoradapter/transit/mappers/RaptorTransitDataMapper.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/mappers/TripPatternForDateMapper.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/RaptorTransitData.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/BoardAlight.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/DefaultTransitDataProviderFilter.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/DefaultTransitDataProviderFilterBuilder.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/RaptorRoutingRequestTransitData.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/RaptorRoutingRequestTransitDataCreator.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/transfercache/RaptorRequestTransferCache.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/transfercache/RaptorRequestTransferCacheKey.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/TransitDataProviderFilter.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/TripPatternForDates.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/TripScheduleAlightSearch.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/TripScheduleBoardSearch.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/TripScheduleSearchFactory.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/TripScheduleWithOffset.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/TripSearchTimetable.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/request/TripTimesForDaysIndex.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/RoutingAccessEgress.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/TransitTuningParameters.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/TripPatternForDate.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/TripSchedule.java
org/opentripplanner/routing/algorithm/RequestPreProcessor.java
org/opentripplanner/routing/algorithm/RoutingResult.java
org/opentripplanner/routing/algorithm/RoutingWorker.java  [MONITORING]
org/opentripplanner/routing/algorithm/RoutingWorkerRequest.java
org/opentripplanner/routing/algorithm/transferoptimization/api/OptimizedPath.java
org/opentripplanner/routing/algorithm/transferoptimization/api/TransferOptimizationParameters.java
org/opentripplanner/routing/algorithm/transferoptimization/api/TransferOptimized.java
org/opentripplanner/routing/algorithm/transferoptimization/configure/TransferOptimizationServiceConfigurator.java
org/opentripplanner/routing/algorithm/transferoptimization/model/BasicStopTime.java
org/opentripplanner/routing/algorithm/transferoptimization/model/costfilter/MinCostPathTailFilter.java
org/opentripplanner/routing/algorithm/transferoptimization/model/costfilter/MinCostPathTailFilterFactory.java
org/opentripplanner/routing/algorithm/transferoptimization/model/MinSafeTransferTimeCalculator.java
org/opentripplanner/routing/algorithm/transferoptimization/model/OptimizedPathTail.java
org/opentripplanner/routing/algorithm/transferoptimization/model/passthrough/PassThroughPathTailFilter.java
org/opentripplanner/routing/algorithm/transferoptimization/model/passthrough/PassThroughPointsIterator.java
org/opentripplanner/routing/algorithm/transferoptimization/model/passthrough/PathTailC2Calculator.java
org/opentripplanner/routing/algorithm/transferoptimization/model/PathTailFilter.java
org/opentripplanner/routing/algorithm/transferoptimization/model/StopPriorityCostCalculator.java
org/opentripplanner/routing/algorithm/transferoptimization/model/StopTime.java
org/opentripplanner/routing/algorithm/transferoptimization/model/TransferWaitTimeCostCalculator.java
org/opentripplanner/routing/algorithm/transferoptimization/model/TripStopTime.java
org/opentripplanner/routing/algorithm/transferoptimization/model/TripToTripTransfer.java
org/opentripplanner/routing/algorithm/transferoptimization/OptimizeTransferService.java
org/opentripplanner/routing/algorithm/transferoptimization/services/OptimizePathDomainService.java
org/opentripplanner/routing/algorithm/transferoptimization/services/TransferGenerator.java  [JDK17]
org/opentripplanner/routing/algorithm/transferoptimization/services/TransferServiceAdaptor.java
org/opentripplanner/routing/algorithm/transferoptimization/services/TransitPathLegSelector.java
org/opentripplanner/routing/algorithm/via/ViaRoutingWorker.java
org/opentripplanner/routing/api/request/DebugEventType.java
org/opentripplanner/routing/api/request/DebugRaptor.java
org/opentripplanner/routing/api/request/DebugRaptorBuilder.java
org/opentripplanner/routing/api/request/framework/AbstractLinearFunction.java
org/opentripplanner/routing/api/request/framework/CostLinearFunction.java
org/opentripplanner/routing/api/request/framework/DurationForEnum.java
org/opentripplanner/routing/api/request/framework/LinearFunctionSerialization.java
org/opentripplanner/routing/api/request/framework/TimeAndCostPenalty.java
org/opentripplanner/routing/api/request/framework/TimeAndCostPenaltyForEnum.java
org/opentripplanner/routing/api/request/framework/TimePenalty.java
org/opentripplanner/routing/api/request/preference/AccessEgressPreferences.java
org/opentripplanner/routing/api/request/preference/AccessibilityPreferences.java
org/opentripplanner/routing/api/request/preference/BikePreferences.java
org/opentripplanner/routing/api/request/preference/CarPreferences.java
org/opentripplanner/routing/api/request/preference/DirectTransitPreferences.java
org/opentripplanner/routing/api/request/preference/ElevatorPreferences.java
org/opentripplanner/routing/api/request/preference/EscalatorPreferences.java
org/opentripplanner/routing/api/request/preference/filter/VehicleParkingFilter.java
org/opentripplanner/routing/api/request/preference/filter/VehicleParkingSelect.java
org/opentripplanner/routing/api/request/preference/ItineraryFilterDebugProfile.java
org/opentripplanner/routing/api/request/preference/ItineraryFilterPreferences.java
org/opentripplanner/routing/api/request/preference/MaxStopCountLimit.java
org/opentripplanner/routing/api/request/preference/RaptorPreferences.java
org/opentripplanner/routing/api/request/preference/RoutingPreferences.java
org/opentripplanner/routing/api/request/preference/RoutingPreferencesBuilder.java
org/opentripplanner/routing/api/request/preference/ScooterPreferences.java
org/opentripplanner/routing/api/request/preference/StreetPreferences.java
org/opentripplanner/routing/api/request/preference/SystemPreferences.java
org/opentripplanner/routing/api/request/preference/TimeSlopeSafetyTriangle.java
org/opentripplanner/routing/api/request/preference/TransferOptimizationPreferences.java
org/opentripplanner/routing/api/request/preference/TransferPreferences.java
org/opentripplanner/routing/api/request/preference/TransitPreferences.java
org/opentripplanner/routing/api/request/preference/VehicleParkingPreferences.java
org/opentripplanner/routing/api/request/preference/VehicleRentalPreferences.java
org/opentripplanner/routing/api/request/preference/VehicleWalkingPreferences.java
org/opentripplanner/routing/api/request/preference/WalkPreferences.java
org/opentripplanner/routing/api/request/preference/WheelchairPreferences.java
org/opentripplanner/routing/api/request/request/filter/AllowAllTransitFilter.java
org/opentripplanner/routing/api/request/request/filter/SelectRequest.java
org/opentripplanner/routing/api/request/request/filter/TransitFilter.java
org/opentripplanner/routing/api/request/request/filter/TransitFilterRequest.java
org/opentripplanner/routing/api/request/request/filter/TransitGroupSelect.java
org/opentripplanner/routing/api/request/request/JourneyRequest.java
org/opentripplanner/routing/api/request/request/JourneyRequestBuilder.java
org/opentripplanner/routing/api/request/request/StreetRequest.java
org/opentripplanner/routing/api/request/request/TransitRequest.java
org/opentripplanner/routing/api/request/request/TransitRequestBuilder.java
org/opentripplanner/routing/api/request/RequestModes.java
org/opentripplanner/routing/api/request/RequestModesBuilder.java
org/opentripplanner/routing/api/request/RouteRequest.java
org/opentripplanner/routing/api/request/RouteRequestBuilder.java
org/opentripplanner/routing/api/request/RouteViaRequest.java
org/opentripplanner/routing/api/request/RoutingTag.java
org/opentripplanner/routing/api/request/TripLocation.java
org/opentripplanner/routing/api/request/TripOnDateReference.java
org/opentripplanner/routing/api/request/TripOnDateReferenceWithTripAndDate.java
org/opentripplanner/routing/api/request/TripOnDateReferenceWithTripOnServiceDateId.java
org/opentripplanner/routing/api/request/via/AbstractViaLocation.java
org/opentripplanner/routing/api/request/via/ViaLocation.java
org/opentripplanner/routing/api/request/via/VisitViaLocation.java
org/opentripplanner/routing/api/request/ViaLocationDeprecated.java
org/opentripplanner/routing/api/response/InputField.java
org/opentripplanner/routing/api/response/RoutingError.java
org/opentripplanner/routing/api/response/RoutingErrorCode.java
org/opentripplanner/routing/api/response/RoutingResponse.java
org/opentripplanner/routing/api/response/TripSearchMetadata.java
org/opentripplanner/routing/api/response/ViaRoutingResponse.java
org/opentripplanner/routing/api/response/ViaRoutingResponseConnection.java
org/opentripplanner/routing/api/RoutingService.java
org/opentripplanner/routing/cost/CostLimit.java
org/opentripplanner/routing/error/InvalidRoutingInputException.java
org/opentripplanner/routing/error/PathNotFoundException.java
org/opentripplanner/routing/error/RoutingValidationException.java
org/opentripplanner/routing/framework/DebugTimingAggregator.java  [MONITORING]
org/opentripplanner/routing/framework/MicrometerUtils.java  [MONITORING]
org/opentripplanner/routing/impl/TransitAlertServiceImpl.java
org/opentripplanner/routing/linking/internal/VertexCreationService.java
org/opentripplanner/routing/linking/LinkingContext.java
org/opentripplanner/routing/linking/LinkingContextFactory.java  [JDK17]
org/opentripplanner/routing/linking/LinkingContextRequest.java
org/opentripplanner/routing/linking/LinkingContextRequestBuilder.java
org/opentripplanner/routing/linking/mapping/LinkingContextRequestMapper.java
org/opentripplanner/routing/linking/SameEdgeAdjuster.java
org/opentripplanner/routing/service/DefaultRoutingService.java  [MONITORING]
org/opentripplanner/routing/services/TransitAlertService.java
org/opentripplanner/routing/util/DiffEntry.java
org/opentripplanner/routing/util/DiffList.java
org/opentripplanner/routing/util/DiffTool.java
org/opentripplanner/routing/via/model/ViaCoordinateTransfer.java
org/opentripplanner/routing/via/service/DefaultViaCoordinateTransferFactory.java
org/opentripplanner/routing/via/ViaCoordinateTransferFactory.java
org/opentripplanner/service/paging/PagingService.java
org/opentripplanner/service/streetdetails/internal/DefaultStreetDetailsService.java
org/opentripplanner/service/streetdetails/model/InclinedEdgeLevelInfo.java
org/opentripplanner/service/streetdetails/model/Level.java
org/opentripplanner/service/streetdetails/model/VertexLevelInfo.java
org/opentripplanner/service/streetdetails/StreetDetailsRepository.java
org/opentripplanner/service/streetdetails/StreetDetailsService.java
org/opentripplanner/standalone/config/BuildConfig.java  [STUB]
org/opentripplanner/standalone/config/OtpConfig.java  [STUB]
org/opentripplanner/standalone/config/RouterConfig.java  [STUB]
org/opentripplanner/standalone/config/routerconfig/TransitRoutingConfig.java  [STUB]
org/opentripplanner/streetadapter/StreetSearchRequestMapper.java
org/opentripplanner/transfer/constrained/ConstrainedTransferService.java
org/opentripplanner/transfer/constrained/internal/DefaultConstrainedTransferService.java
org/opentripplanner/transfer/constrained/internal/TransferPointMap.java
org/opentripplanner/transfer/constrained/model/ConstrainedTransfer.java
org/opentripplanner/transfer/constrained/model/RouteStationTransferPoint.java
org/opentripplanner/transfer/constrained/model/RouteStopTransferPoint.java
org/opentripplanner/transfer/constrained/model/StationTransferPoint.java
org/opentripplanner/transfer/constrained/model/StopTransferPoint.java
org/opentripplanner/transfer/constrained/model/TransferConstraint.java
org/opentripplanner/transfer/constrained/model/TransferPoint.java
org/opentripplanner/transfer/constrained/model/TransferPriority.java
org/opentripplanner/transfer/constrained/model/TripTransferPoint.java
org/opentripplanner/transfer/constrained/raptoradaptor/ConstrainedBoardingSearch.java
org/opentripplanner/transfer/constrained/raptoradaptor/ConstrainedBoardingSearchForward.java
org/opentripplanner/transfer/constrained/raptoradaptor/ConstrainedBoardingSearchReverse.java
org/opentripplanner/transfer/constrained/raptoradaptor/ConstrainedBoardingSearchStrategy.java
org/opentripplanner/transfer/constrained/raptoradaptor/ConstrainedTransferBoarding.java
org/opentripplanner/transfer/constrained/raptoradaptor/ConstrainedTransfersForPatterns.java
org/opentripplanner/transfer/constrained/raptoradaptor/TransferForPattern.java
org/opentripplanner/transfer/constrained/raptoradaptor/TransferForPatternByStopPos.java
org/opentripplanner/transfer/constrained/raptoradaptor/TransferIndexGenerator.java
org/opentripplanner/transfer/constrained/raptoradaptor/TransferPointForPatternFactory.java
org/opentripplanner/transfer/constrained/raptoradaptor/TransferPointMatcher.java
org/opentripplanner/transfer/regular/index/OnDemandRaptorTransferIndex.java
org/opentripplanner/transfer/regular/index/PreCachedRaptorTransferIndex.java
org/opentripplanner/transfer/regular/index/RaptorTransferIndex.java
org/opentripplanner/transfer/regular/internal/DefaultTransferService.java
org/opentripplanner/transfer/regular/model/DefaultRaptorTransfer.java
org/opentripplanner/transfer/regular/model/PathTransfer.java
org/opentripplanner/transfer/regular/model/TransfersMapper.java
org/opentripplanner/transfer/regular/RegularTransferService.java
org/opentripplanner/transfer/regular/TransferRepository.java
org/opentripplanner/transit/api/model/EmptyIsEverythingFilter.java
org/opentripplanner/transit/api/model/FilterValues.java
org/opentripplanner/transit/api/model/NullIsEverythingFilter.java
org/opentripplanner/transit/api/model/RequiredFilterValues.java
org/opentripplanner/transit/api/request/CancellationPolicy.java
org/opentripplanner/transit/api/request/FindRegularStopsByBoundingBoxRequest.java
org/opentripplanner/transit/api/request/FindRegularStopsByBoundingBoxRequestBuilder.java
org/opentripplanner/transit/api/request/FindRoutesRequest.java
org/opentripplanner/transit/api/request/FindRoutesRequestBuilder.java
org/opentripplanner/transit/api/request/FindStopLocationsRequest.java
org/opentripplanner/transit/api/request/FindStopLocationsRequestBuilder.java
org/opentripplanner/transit/api/request/TransitAlertRequest.java
org/opentripplanner/transit/api/request/TransitAlertRequestBuilder.java
org/opentripplanner/transit/api/request/TripOnServiceDateRequest.java  [JDK17]
org/opentripplanner/transit/api/request/TripOnServiceDateRequestBuilder.java
org/opentripplanner/transit/api/request/TripRequest.java
org/opentripplanner/transit/api/request/TripRequestBuilder.java
org/opentripplanner/transit/api/request/TripTimeOnDateRequest.java
org/opentripplanner/transit/api/request/TripTimeOnDateRequestBuilder.java
org/opentripplanner/transit/EntranceResolver.java
org/opentripplanner/transit/model/basic/MainAndSubMode.java
org/opentripplanner/transit/model/basic/Money.java
org/opentripplanner/transit/model/basic/NarrowedTransitMode.java
org/opentripplanner/transit/model/basic/Notice.java
org/opentripplanner/transit/model/basic/NoticeBuilder.java
org/opentripplanner/transit/model/basic/ReplacementRequirement.java
org/opentripplanner/transit/model/basic/SubMode.java
org/opentripplanner/transit/model/basic/TransitMode.java
org/opentripplanner/transit/model/calendar/TripCalendars.java
org/opentripplanner/transit/model/filter/expr/AndMatcher.java
org/opentripplanner/transit/model/filter/expr/BinaryOperator.java
org/opentripplanner/transit/model/filter/expr/CaseInsensitiveStringPrefixMatcher.java
org/opentripplanner/transit/model/filter/expr/ContainsMatcher.java
org/opentripplanner/transit/model/filter/expr/EqualityMatcher.java
org/opentripplanner/transit/model/filter/expr/ExpressionBuilder.java
org/opentripplanner/transit/model/filter/expr/GenericUnaryMatcher.java
org/opentripplanner/transit/model/filter/expr/Matcher.java
org/opentripplanner/transit/model/filter/expr/NegationMatcher.java
org/opentripplanner/transit/model/filter/expr/NullSafeWrapperMatcher.java
org/opentripplanner/transit/model/filter/expr/OrMatcher.java
org/opentripplanner/transit/model/filter/selector/FilterRequest.java
org/opentripplanner/transit/model/filter/selector/SelectorBasedMatcherFactory.java
org/opentripplanner/transit/model/filter/transit/RegularStopMatcherFactory.java
org/opentripplanner/transit/model/filter/transit/RouteMatcherFactory.java
org/opentripplanner/transit/model/filter/transit/StopLocationMatcherFactory.java
org/opentripplanner/transit/model/filter/transit/TransitAlertMatcherFactory.java
org/opentripplanner/transit/model/filter/transit/TransitAlertSelectRequest.java
org/opentripplanner/transit/model/filter/transit/TripMatcherFactory.java
org/opentripplanner/transit/model/filter/transit/TripOnServiceDateMatcherFactory.java
org/opentripplanner/transit/model/filter/transit/TripOnServiceDateSelectRequest.java
org/opentripplanner/transit/model/filter/transit/TripTimeOnDateMatcherFactory.java
org/opentripplanner/transit/model/filter/transit/TripTimeOnDateSelectRequest.java
org/opentripplanner/transit/model/framework/AbstractBuilder.java
org/opentripplanner/transit/model/framework/AbstractEntityBuilder.java
org/opentripplanner/transit/model/framework/AbstractTransitEntity.java
org/opentripplanner/transit/model/framework/DataValidationException.java
org/opentripplanner/transit/model/framework/Deduplicator.java
org/opentripplanner/transit/model/framework/DefaultEntityById.java
org/opentripplanner/transit/model/framework/EntityById.java
org/opentripplanner/transit/model/framework/EntityContext.java
org/opentripplanner/transit/model/framework/EntityNotFoundException.java
org/opentripplanner/transit/model/framework/ImmutableEntityById.java
org/opentripplanner/transit/model/framework/LogInfo.java
org/opentripplanner/transit/model/framework/TransitBuilder.java
org/opentripplanner/transit/model/framework/TransitEntity.java
org/opentripplanner/transit/model/framework/TransitEntityBuilder.java
org/opentripplanner/transit/model/framework/TransitObject.java
org/opentripplanner/transit/model/network/BikeAccess.java
org/opentripplanner/transit/model/network/CarAccess.java
org/opentripplanner/transit/model/network/GroupOfRoutes.java
org/opentripplanner/transit/model/network/GroupOfRoutesBuilder.java
org/opentripplanner/transit/model/network/grouppriority/BinarySetOperator.java
org/opentripplanner/transit/model/network/grouppriority/DefaultTransitGroupPriorityCalculator.java
org/opentripplanner/transit/model/network/grouppriority/EntityAdapter.java
org/opentripplanner/transit/model/network/grouppriority/Matcher.java
org/opentripplanner/transit/model/network/grouppriority/Matchers.java
org/opentripplanner/transit/model/network/grouppriority/TransitGroupPriority32n.java
org/opentripplanner/transit/model/network/grouppriority/TransitGroupPriorityService.java
org/opentripplanner/transit/model/network/grouppriority/TripAdapter.java
org/opentripplanner/transit/model/network/grouppriority/TripPatternAdapter.java
org/opentripplanner/transit/model/network/ReplacedByRelation.java
org/opentripplanner/transit/model/network/ReplacementForRelation.java
org/opentripplanner/transit/model/network/Route.java
org/opentripplanner/transit/model/network/RouteBuilder.java
org/opentripplanner/transit/model/network/RoutingTripPattern.java
org/opentripplanner/transit/model/network/StopPattern.java
org/opentripplanner/transit/model/network/TripPattern.java
org/opentripplanner/transit/model/network/TripPatternBuilder.java
org/opentripplanner/transit/model/organization/Agency.java
org/opentripplanner/transit/model/organization/AgencyBuilder.java
org/opentripplanner/transit/model/organization/Branding.java
org/opentripplanner/transit/model/organization/BrandingBuilder.java
org/opentripplanner/transit/model/organization/ContactInfo.java
org/opentripplanner/transit/model/organization/ContactInfoBuilder.java
org/opentripplanner/transit/model/organization/Operator.java
org/opentripplanner/transit/model/organization/OperatorBuilder.java
org/opentripplanner/transit/model/site/AreaStop.java
org/opentripplanner/transit/model/site/AreaStopBuilder.java
org/opentripplanner/transit/model/site/BoardingArea.java
org/opentripplanner/transit/model/site/BoardingAreaBuilder.java
org/opentripplanner/transit/model/site/Entrance.java
org/opentripplanner/transit/model/site/EntranceBuilder.java
org/opentripplanner/transit/model/site/FareZone.java
org/opentripplanner/transit/model/site/FareZoneBuilder.java
org/opentripplanner/transit/model/site/GroupOfStations.java
org/opentripplanner/transit/model/site/GroupOfStationsBuilder.java
org/opentripplanner/transit/model/site/GroupOfStationsPurpose.java
org/opentripplanner/transit/model/site/GroupStop.java
org/opentripplanner/transit/model/site/GroupStopBuilder.java
org/opentripplanner/transit/model/site/MultiModalStation.java
org/opentripplanner/transit/model/site/MultiModalStationBuilder.java
org/opentripplanner/transit/model/site/RegularStop.java
org/opentripplanner/transit/model/site/RegularStopBuilder.java
org/opentripplanner/transit/model/site/Station.java
org/opentripplanner/transit/model/site/StationBuilder.java
org/opentripplanner/transit/model/site/StationElement.java
org/opentripplanner/transit/model/site/StationElementBuilder.java
org/opentripplanner/transit/model/site/StopLevel.java
org/opentripplanner/transit/model/site/StopLocation.java
org/opentripplanner/transit/model/site/StopLocationsGroup.java
org/opentripplanner/transit/model/site/StopTransferPriority.java
org/opentripplanner/transit/model/site/StopType.java
org/opentripplanner/transit/model/timetable/booking/BookingInfo.java
org/opentripplanner/transit/model/timetable/booking/BookingInfoBuilder.java
org/opentripplanner/transit/model/timetable/booking/BookingMethod.java
org/opentripplanner/transit/model/timetable/booking/BookingTime.java
org/opentripplanner/transit/model/timetable/Direction.java
org/opentripplanner/transit/model/timetable/FrequencyEntry.java
org/opentripplanner/transit/model/timetable/OccupancyStatus.java
org/opentripplanner/transit/model/timetable/PartialReplacedBy.java
org/opentripplanner/transit/model/timetable/RealTimeTripState.java
org/opentripplanner/transit/model/timetable/RealTimeTripTimes.java
org/opentripplanner/transit/model/timetable/RealTimeTripTimesBuilder.java
org/opentripplanner/transit/model/timetable/ScheduledTripTimes.java
org/opentripplanner/transit/model/timetable/ScheduledTripTimesBuilder.java
org/opentripplanner/transit/model/timetable/StopRealTimeState.java
org/opentripplanner/transit/model/timetable/StopTimeKey.java
org/opentripplanner/transit/model/timetable/StopTimeKeyBuilder.java
org/opentripplanner/transit/model/timetable/Timetable.java  [JDK17]
org/opentripplanner/transit/model/timetable/TimetableBuilder.java  [JDK17]
org/opentripplanner/transit/model/timetable/TimetableValidationError.java
org/opentripplanner/transit/model/timetable/Trip.java
org/opentripplanner/transit/model/timetable/TripAlteration.java
org/opentripplanner/transit/model/timetable/TripBuilder.java
org/opentripplanner/transit/model/timetable/TripIdAndServiceDate.java
org/opentripplanner/transit/model/timetable/TripOnServiceDate.java
org/opentripplanner/transit/model/timetable/TripOnServiceDateBuilder.java
org/opentripplanner/transit/model/timetable/TripTimes.java
org/opentripplanner/transit/repository/TimetableRepositorySnapshot.java
org/opentripplanner/transit/service/ArrivalDeparture.java
org/opentripplanner/transit/service/DefaultTransitService.java
org/opentripplanner/transit/service/ReplacementHelper.java
org/opentripplanner/transit/service/SiteRepository.java
org/opentripplanner/transit/service/SiteRepositoryBuilder.java
org/opentripplanner/transit/service/StopModelIndex.java
org/opentripplanner/transit/service/StopTimesHelper.java  [JDK17]
org/opentripplanner/transit/service/TransitRepository.java
org/opentripplanner/transit/service/TransitRepositoryIndex.java
org/opentripplanner/transit/service/TransitService.java
org/opentripplanner/transit/service/TransitServiceResolver.java
org/opentripplanner/transit/service/TripTimesHelper.java
org/opentripplanner/transit/SiteResolver.java
org/opentripplanner/transit/StopResolver.java
org/opentripplanner/updater/configure/UpdaterConfigurator.java  [STUB]
org/opentripplanner/updater/GraphUpdaterManager.java  [STUB]
org/opentripplanner/updater/GraphUpdaterStatus.java  [STUB]
```

## 3. Stubs and replaced files
See `STUBS.md` for the full list of stub files (marked `[STUB]` above), their members, and the
files that reference them — 27 boundary/`ext.*` stubs plus 3 files replaced by stubs instead of
copied (marked `[STUB - replaced]` above: `DirectFlexRouter.java`, `FlexAccessEgressRouter.java`,
`FlexAccessEgressAdapter.java`).

## 4. Edited files
Every file below is verbatim-copied from upstream except for the specific edit named. Nothing
else in the file was reformatted, renamed, or otherwise touched.

### JDK 17 downgrades
- `routing/algorithm/raptoradapter/router/startonboardaccess/TripAndServiceDateResolver.java`:
  replaced a `switch` over record-deconstruction patterns (JDK 21) with an `instanceof` pattern
  chain (JDK 16+), same outcomes.
- `model/plan/ItinerariesCalculateLegTotals.java`: replaced `getFirst`/`getLast`
- `model/plan/Itinerary.java`: replaced `getFirst`/`getLast` (6 call sites)
- `routing/algorithm/filterchain/filters/system/mcmax/Group.java`: replaced `getFirst`
- `routing/algorithm/filterchain/filters/system/mcmax/State.java`: replaced `getFirst`
- `routing/algorithm/mapping/RaptorPathToItineraryMapper.java`: replaced `getFirst`
- `routing/algorithm/mapping/StatesToWalkStepsMapper.java`: replaced `getLast`
- `routing/algorithm/mapping/StreetPathToLegsMapper.java`: replaced `getFirst`/`getLast`
- `routing/algorithm/mapping/TripPlanMapper.java`: replaced `getFirst`/`getLast`
- `routing/algorithm/raptoradapter/router/startonboardaccess/TripLocationResolver.java`: replaced
  `getFirst`
- `routing/algorithm/transferoptimization/services/TransferGenerator.java`: replaced
  `getFirst`/`getLast`
- `routing/linking/LinkingContextFactory.java`: replaced `getFirst`/`getLast`
- `transit/api/request/TripOnServiceDateRequest.java`: replaced `getFirst` (3 call sites)
- `transit/model/timetable/Timetable.java`: replaced `getLast`
- `transit/model/timetable/TimetableBuilder.java`: replaced `HashMap.newHashMap(int)` (JDK 19+)
  with `new HashMap<>()`
- `transit/service/StopTimesHelper.java`: replaced `getFirst`

### Monitoring removal
Micrometer (`io.micrometer.core.instrument.*`) usages are never deleted — every upstream line that
depended on Micrometer is retained, commented out, inside a block whose first line is
`// MONITORING REMOVED:`. Inside parameter/argument lists this uses an inline
`/* MONITORING REMOVED: ... */` block comment (since a `//` line comment cannot sit mid-list); in
method/class bodies it is a `// MONITORING REMOVED:` line followed by the original lines each
re-prefixed `// `. Un-commenting every marked block (and restoring the Micrometer dependency)
reconstructs the upstream file, modulo the markers themselves and the handful of minimal no-op
replacement statements (an empty method body, a `return` of a neutral value, or `body.run()` in
place of `timer.record(body)`) that are the module's actual (non-Micrometer) behavior:
- `routing/algorithm/RoutingWorker.java`: `MeterRegistry` import, field, constructor parameter,
  and constructor-body assignment commented out; the `DebugTimingAggregator(...)` and
  `TransitRouter.route(...)` call-site arguments commented out in place.
- `routing/algorithm/raptoradapter/router/TransitRouter.java`: `MeterRegistry` import, field,
  both constructors' parameters and the private constructor's body assignment commented out; the
  `new TransitRouter(...)` and `RaptorRequestMapper.of(...)` call-site arguments commented out in
  place.
- `routing/algorithm/raptoradapter/transit/mappers/RaptorRequestMapper.java`: `MeterRegistry`
  import, field, both the private constructor's and the static `of(...)` factory's parameters,
  the constructor-body assignment, and the `new RaptorRequestMapper<T>(...)` call-site argument
  all commented out; the now-unused `javax.annotation.Nullable` import is commented out too; the
  `if (meterRegistry != null) { builder.performanceTimers(new PerformanceTimersForRaptor(...,
  meterRegistry)); }` block is commented out in full and replaced with the unconditional
  `builder.performanceTimers(new PerformanceTimersForRaptor(...))` (no registry to guard on).
- `routing/framework/DebugTimingAggregator.java`: every Micrometer-only import, field (all
  `Clock`/`Timer`/`DistributionSummary`/`Timer.Sample`/timing fields, `LOG`, `NANOS_TO_MILLIS`),
  the two-arg constructor's body, the private `log(...)` helper, and the real body of every public
  method are commented out in full; each public method's active body is now empty (a no-op), and
  `finishedRendering()`/`getDebugOutput()` additionally keep a one-line active `return` of an
  all-zero `DebugOutput`.
- `routing/framework/MicrometerUtils.java`: `Tag` import and the original
  `mapTimingTags` method body (which built a `List<Tag>`) commented out in full; the active
  replacement method (same name, `List<Object>` return type since `Tag` isn't on the classpath)
  returns an empty list.
- `routing/algorithm/raptoradapter/router/performance/PerformanceTimersForRaptor.java`:
  `MeterRegistry`/`Timer` imports, the `MicrometerUtils` import, the `Timer`/`MeterRegistry`
  fields, the constructor's `MeterRegistry` parameter and its Micrometer-based body, and each
  `Timer.record(body)` call commented out in full; `route`/`routeTransit`/`applyTransfers` now
  actively just run the given `Runnable` body, and `withNamePrefix(...)` no longer threads a
  registry through.
- `routing/service/DefaultRoutingService.java`: `MeterRegistry` import, field, constructor
  parameter, and constructor-body assignment commented out; the `new RoutingWorker(...)`
  call-site argument commented out in place.

### Authorized edit (Task 1: `Serializable` for `LoadedNetwork` round trip)
`LoadedNetwork`'s Java-serialization round trip needs the whole reachable object graph to be
`Serializable`. Both edits below are single-line `implements Serializable` markers, nothing else
in either file was touched.
- `transfer/regular/internal/TransferIndex.java`: now `implements Serializable` (a no-op marker;
  this class has zero fields) because `DefaultTransferRepository`'s `TransferIndex` field is
  non-transient.
- `transfer/constrained/internal/TransferPointMap.java`: the outer class and its three private key
  records (`TripKey`, `RouteStopKey`, `RouteStationKey`) each now `implements Serializable`,
  because `DefaultConstrainedTransferService` (which already declares `Serializable`) holds a
  `TransferPointMap`.

## 5. How to re-sync
1. Delete `src/main/java`.
2. Re-run the compiler-driven copy loop from the seeds listed in the task brief against the new
   upstream commit, following the same exclusion list.
3. Re-apply the JDK 17 downgrades and monitoring removal listed in section 4, re-checking each
   against the new upstream source (a newer OTP version may have already fixed some of these, or
   introduced new JDK-21-only syntax elsewhere).
4. Re-create the stubs in `STUBS.md`, adjusting members if the call sites that need them changed.
5. Re-run `./gradlew :otp-routing:compileJava`, re-diff every copied file against upstream, and
   re-check the excluded-package grep before committing.

---

# Task 5 addendum: `src/testFixtures/java` and `src/test/java`

## 6. What was copied (fixtures and tests)

`src/testFixtures/java` holds 24 files and `src/test/java` holds 301 files, both compiler-driven
copies from upstream `application/src/test-fixtures/java` and `application/src/test/java`
(commit 61a3af6798), verbatim via `cp` except the files listed in section 8 below (edited) and
the relocations in section 9. `com.google.truth:truth:1.4.5`, the JUnit 6.1.2 BOM
(`junit-jupiter` + `junit-platform-launcher`), and (for `src/testFixtures/java`)
`org.locationtech.jts:jts-core:1.20.0` / `com.google.code.findbugs:jsr305:3.0.2` /
`junit-jupiter-api` / `project(":otp-utils")` / `project(":otp-raptor")` /
`testFixtures(project(":otp-street"))` were added to `build.gradle.kts` per the task brief; no
Mockito was needed (no copied test imports `org.mockito`).

`src/testFixtures/java` (24 files):

```
org/opentripplanner/_support/geometry/Coordinates.java  [relocated]
org/opentripplanner/core/model/id/FeedScopedIdForTestFactory.java
org/opentripplanner/LocalTimeParser.java  [relocated]
org/opentripplanner/routing/algorithm/raptoradapter/transit/RaptorTransitDataTestFactory.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/TestSlackProvider.java
org/opentripplanner/routing/algorithm/raptoradapter/transit/TransitTuningParametersTestFactory.java
org/opentripplanner/routing/linking/TransitStopVertexBuilderFactory.java
org/opentripplanner/routing/linking/VertexLinkerTestFactory.java
org/opentripplanner/street/model/StreetModelForTest.java
org/opentripplanner/transfer/regular/TransferServiceTestFactory.java  [STUB-RELATED]
org/opentripplanner/transit/model/_data/PatternTestModel.java  [relocated]
org/opentripplanner/transit/model/_data/TransitRepositoryForTest.java  [relocated, STUB-RELATED]
org/opentripplanner/transit/model/_data/TripTimesForTest.java  [relocated]
org/opentripplanner/transit/model/RaptorTransitDataFetcher.java
org/opentripplanner/transit/model/SiteRepositoryTestBuilder.java
org/opentripplanner/transit/model/TestTransitTuningParameters.java
org/opentripplanner/transit/model/TransitRepositoryTestBuilder.java  [STUB-RELATED (brief), JDK17]
org/opentripplanner/transit/model/TransitTestEnvironment.java  [JDK17]
org/opentripplanner/transit/model/TransitTestEnvironmentBuilder.java
org/opentripplanner/transit/model/TripInput.java
org/opentripplanner/transit/model/TripOnDateDataFetcher.java
org/opentripplanner/transit/model/TripTimesStateDecoder.java
org/opentripplanner/transit/model/TripTimesStringBuilder.java
org/opentripplanner/transit/service/NoopSiteResolver.java  [Authorized edit]
```

Note on `street/search/state/TestStateBuilder.java`: the task brief's Interfaces section says
`TestStateBuilder` (along with `StreetModelFactory`, `LinkingEnvironmentBuilder`,
`ElevationProfiles`) "come from `:otp-street`'s testFixtures variant and are not re-copied." That
holds for the other three, but upstream `application` has its **own** file at the identical
package + class name (`org.opentripplanner.street.search.state.TestStateBuilder`), richer than
`:otp-street`'s version: it adds `stop(RegularStop)`, `escalatorEdge()`, `stairsEdge()` and
`escalatorEdgeAndStationEntrance()`, none of which exist on the `:otp-street` fixture. Eight
surviving tests (`StatesToWalkStepsMapperTest`, `NearbyStopFinderVisitorTest`,
`StopFinderTraverseVisitorTest`, `LegsToItineraryMapperTest`, `StreetPathToLegsMapperTest`,
`AccessEgressesTest`, `AccessEgressPenaltyDecoratorTest`, `DefaultAccessEgressTest`) need these.
This file was originally copied into
`src/testFixtures/java/org/opentripplanner/street/search/state/TestStateBuilder.java`, relying on
Gradle resolving a project's own sources before its dependencies' classes so it would shadow
`:otp-street`'s simpler fixture for `:otp-routing`'s own compilation only. That placement turned
out to be unsafe on Android: both `:otp-routing`'s and `:otp-street`'s testFixtures jars reach the
instrumentation APK via `testFixtures(project(...))`, and since both jars ship a class with this
same fully-qualified name, D8 failed with a duplicate-class error. The file has since been
relocated (see section 9) to
`src/test/java/org/opentripplanner/street/search/state/TestStateBuilder.java`, where it still
shadows `:otp-street`'s testFixtures fixture on `:otp-routing`'s own test classpath (test sources
shadow dependency jars, so the same effective precedence holds) but is no longer published in
`:otp-routing`'s own testFixtures jar, avoiding the duplicate class. No file under
`src/testFixtures/java` references it; only the eight `src/test/java` tests listed above do.
`:otp-street`'s own tests are unaffected, since they compile and run in a separate
module/classpath. This is a deviation from the literal brief text, made because the compiler
required it; flagged here for review.

`src/test/java` (301 files): see `find src/test/java -name '*.java' | sort` for the full list;
the dominant packages are `routing/algorithm/*` (raptoradapter, filterchain, mapping,
transferoptimization — ~110 files), `transit/model/*` (~45 files), `routing/api/request/*`
(~35 files), and `model/plan/*` (~15 files), matching the task brief's expectation.

## 7. Deleted tests

Every deletion below is a whole file (never an individual test method), with the specific reason.

**GTFS/NeTEx/OSM graph-building test infrastructure** (server-only test infrastructure per the
task brief):
- `GtfsTest.java` — references excluded `org.opentripplanner.gtfs.*`, `updater.*`,
  `standalone.*` (GTFS graph-building harness).
- `routing/api/DefaultRoutingServiceTest.java` — extends `GtfsTest`.
- `ConstantsForTests.java` — real GTFS/NeTEx/OSM graph-building test harness referencing excluded
  `graph_builder.*`, `gtfs.*`, `netex.*`, `osm.*`, `ext.fares.*` packages.
- `model/ShapeGeometryTest.java`, `transit/repository/DefaultTimetableRepositoryTest.java`,
  `transit/service/StopTimesHelperTest.java`, `transit/service/TransitRepositoryTest.java` — call
  `ConstantsForTests.buildGtfsGraph(...)` / `.addGtfsToGraph(...)`.

**Fares subsystem out of scope** (needs `com.fasterxml.jackson.databind` — forbidden, only
`jackson-annotations` is pinned — and/or excluded `ext.fares.*`):
- `TestOtpModel.java` — its `FareServiceFactory` field needs
  `org.opentripplanner.routing.fares.FareServiceFactory` (whose own interface signature needs
  Jackson databind's `JsonNode` and excluded `ext.fares.model.FareRulesData`) and
  `ext.fares.service.gtfs.v1.GtfsFareServiceFactory` (excluded ext package).
- `routing/algorithm/GraphRoutingTest.java` and its subclasses
  `place/nearbystopfinder/StraightLineNearbyStopFinderTest.java`,
  `place/nearbystopfinder/StreetNearbyStopFinderMultipleLinksTest.java`,
  `place/nearbystopfinder/StreetNearbyStopFinderTest.java`,
  `routing/algorithm/raptoradapter/router/street/AccessEgressRouterTest.java`,
  `routing/algorithm/StreetModeLinkingTest.java` — depend on `TestOtpModel`.
- `routing/algorithm/mapping/SnapshotTestBase.java` and its subclasses
  `BikeRentalSnapshotTest.java`, `CarSnapshotTest.java`, `ElevationSnapshotTest.java`,
  `TransitSnapshotTest.java` — depend on `ConstantsForTests`/`TestOtpModel`; also
  Jackson/JSON itinerary-snapshot test infrastructure.
- `model/fare/SerializationTest.java` — imports `ext.fares.model.*`.
- `routing/api/request/preference/SystemPreferencesTest.java` — needs
  `ext.dataoverlay.api.DataOverlayParametersBuilder`/`ParameterName`/`ParameterType`;
  `DataOverlayParameters` is stubbed as an empty opaque type in this slice (no builder).

**Excluded `updater.*` package boundary** (beyond the `GraphUpdaterManager`/`GraphUpdaterStatus`
stub surface):
- `model/plan/legreference/ScheduledTransitLegReferenceTest.java` — constructs
  `new GraphUpdaterManager(new WriteToGraphCallbacks(), ...)`; the real 3-arg constructor (and
  `WriteToGraphCallbacks`) live in the excluded `updater.*`/`updater.spi` tree and our stub only
  exposes a no-arg constructor.
- `transit/service/ReplacementHelperTest.java` — imports `updater.spi`/`updater.trip`/
  `updater.trip.siri`.
- `updater/GtfsRealtimeFuzzyTripMatcherTest.java` — imports
  `com.google.transit.realtime.GtfsRealtime` (GTFS-RT protobuf, forbidden) and
  `updater.trip.gtfs`.

**`ext.flex`/`ext.ridehailing` flex feature exercised directly** (needs real behaviour our stubs
deliberately don't provide):
- `ext/flex/FlexAccessEgressBookingTest.java` — package `org.opentripplanner.ext.flex`
  (excluded), needs `ext.flex.trip.UnscheduledTrip`.
- `routing/algorithm/mapping/RaptorPathToItineraryMapperTest.java` —
  `createItineraryWithOnBoardFlexAccess()` constructs `ext.flex.FlexAccessEgress`/
  `FlexPathDurations` expecting real stored accessor values; our `FlexAccessEgress` stub only
  throws `UnsupportedOperationException`.
- `model/plan/ItineraryTest.java`,
  `routing/algorithm/filterchain/filters/system/FlexSearchWindowFilterTest.java`,
  `routing/algorithm/filterchain/filters/system/OutsideSearchWindowFilterTest.java`,
  `routing/algorithm/filterchain/ItineraryListFilterChainTest.java` — call
  `TestItineraryBuilder.flex(...)`/`.carHail(...)`, which are commented out (STUB-RELATED, see
  section 8) because they depend on excluded `ext.flex.*`/`ext.ridehailing.model.*`.

**Forbidden new libraries** (not in the pinned set: junit-bom/truth/Mockito, plus JTS/jsr305 for
fixtures):
- `routing/algorithm/filterchain/filters/transit/group/RemoveIfFirstOrLastTripIsTheSameTest.java`
  — static-imports `org.ejml.UtilEjml.assertTrue` (EJML).
- `GuavaArchitectureTest.java`, `_support/arch/ArchComponent.java`, `_support/arch/Module.java`,
  `_support/arch/Package.java`, `framework/FrameworkArchitectureTest.java`,
  `OtpArchitectureModules.java`, `transit/model/TransitRepositoryArchitectureTest.java` — ArchUnit
  (`com.tngtech.archunit.*`); the four non-test-suffixed files are ArchUnit support code with no
  other consumer once the three test files above are gone.
- `framework/application/OTPFeatureTest.java` — imports `com.fasterxml.jackson.core`/`.databind`.
- `framework/transaction/internal/UpdateManagerMetricsTest.java` — imports
  `io.micrometer.core.instrument.*` (monitoring already removed from this module's main code).
- `routing/algorithm/raptoradapter/transit/cost/PatternCostCalculatorTest.java` —
  static-imports `graphql.Assert.assertFalse` (GraphQL-Java).
- `standalone/config/CommandLineParametersTest.java` — needs `com.beust.jcommander`.

**`standalone.config.*` is stubbed in this slice** (empty classes; these tests need the real
Jackson-databind-based config parser, `standalone.config.framework.*`, or other excluded/out-of-
scope packages):
- `standalone/config/BuildConfigTest.java`, `CommandLineParametersTest.java` (also above),
  `ExampleConfigTest.java`, `GbfsNetworksConfigTest.java`, `OtpConfigLoaderTest.java`,
  `RouterConfigDocTest.java`, `RouterConfigTest.java`,
  `standalone/config/routerconfig/ServerConfigTest.java`,
  `standalone/config/routerconfig/VectorTileConfigTest.java` (also needs excluded
  `ext.vectortiles.*`/`inspector.vector`), `standalone/config/routerconfig/WarmupConfigTest.java`.

**Excluded `apis.*` package**:
- `routing/algorithm/raptoradapter/transit/request/DefaultTransitDataProviderFilterTest.java` —
  imports `apis.transmodel.model.TransmodelTransportSubmode`.

**Build-generated metadata infrastructure, not a port bug**:
- `model/projectinfo/OtpProjectInfoTest.java` — `projectInfo()` reads the classpath resource
  `otp-project-info.properties`, generated by Maven resource-filtering (`project.version`,
  `git.commit.id`, etc.) during the real OTP build; this Gradle module doesn't generate that
  resource, so `OtpProjectInfo.projectInfo()` falls back to its parse-failure default (version
  `0.0.0`) and the `version.major == 2` assertion fails. The file's other test method,
  `matchesRunningOTPInstance()`, is self-contained and would otherwise pass, but whole-file
  deletion applies.

53 files deleted in total.

## 8. Edited files (fixtures and tests)

### JDK 17 downgrades
- `framework/error/WordList.java` (main) — replaced `getFirst`/`getLast` (2 call sites).
- `testFixtures/.../transit/model/TransitRepositoryTestBuilder.java` — replaced `getFirst`
  (`serviceDates.get(0)`).
- `testFixtures/.../transit/model/TransitTestEnvironment.java` — replaced
  `Thread.ofPlatform().name(...).factory()` (JDK 19+ `Thread.Builder` API) with a lambda
  `ThreadFactory`.
- `test/.../raptorlegacy/_data/transit/TestTransitData.java` — replaced `getFirst`.
- `test/.../model/plan/leg/LegTest.java` — replaced `getFirst` (2 call sites) / `getLast`
  (1 call site).
- `test/.../model/TripTimeOnDateTest.java` — replaced `getFirst` (3 call sites).
- `test/.../routing/algorithm/filterchain/filters/transit/DecorateTransitAlertTest.java` —
  replaced `getFirst` (4 call sites).
- `test/.../routing/algorithm/filterchain/framework/filter/GroupByFilterTest.java` — replaced
  `getFirst`.
- `test/.../routing/algorithm/raptoradapter/transit/RaptorTransitDataTest.java` — replaced
  `getFirst` (3 call sites).
- `test/.../routing/algorithm/raptoradapter/transit/request/RaptorRoutingRequestTransitDataCreatorTest.java`
  — replaced `getFirst`.
- `test/.../routing/algorithm/RoutingResultTest.java` — replaced `getFirst` (3 call sites).
- `test/.../routing/linking/LinkingContextFactoryTest.java` — replaced `getFirst` (4 call sites).
- `test/.../routing/linking/mapping/LinkingContextRequestMapperTest.java` — replaced `getFirst`
  (2 call sites).
- `test/.../transit/model/network/grouppriority/TransitGroupPriorityServiceTest.java` — replaced
  `getFirst` (3 call sites).
- `test/.../transit/service/DefaultTransitServiceTest.java` — replaced `getFirst` (2 call sites).
- `test/.../transit/service/TripTimesOnDateTest.java` — replaced `getFirst` (4 call sites).

### Monitoring removal
- `test/.../routing/algorithm/raptoradapter/transit/mappers/RaptorRequestMapperTest.java` — the
  `RaptorRequestMapper.of(...)` call site's `meterRegistry` argument (a literal `null`) is
  commented out in place with `/* MONITORING REMOVED: null, */`, matching Task 4's removal of
  that parameter from `RaptorRequestMapper.of(...)`.

### STUB-RELATED (flex/ridehailing code depending on excluded `ext.*`)
The task brief pre-authorized one `// STUB-RELATED:` comment-out
(`TransitRepositoryTestBuilder.java`'s flex-trip helper). The same treatment — commenting out
just the offending method(s)/import(s), leaving everything else in the file verbatim — was
applied to four more fixture/test-support files the compiler surfaced, each a widely-used shared
file where whole-file deletion would have cost many unrelated, valid tests. Flagged here for
review since it extends the brief's single named instance:
- `testFixtures/.../transit/model/TransitRepositoryTestBuilder.java` — (brief-authorized) the
  flex-trip helper in `trip(...)` and the `UnscheduledTrip` import.
- `testFixtures/.../transfer/regular/TransferServiceTestFactory.java` — `withFlex()` method +
  `ext.flex.FlexTransferIndex` import (`FlexTransferIndex` lives in `application/src/ext`, an
  excluded source root).
- `testFixtures/.../transit/model/_data/TransitRepositoryForTest.java` — `unscheduledTrip(...)`
  (2 overloads) and `scheduledDeviatedTrip(...)` methods + `ScheduledDeviatedTrip`/
  `UnscheduledTrip` imports.
- `test/.../model/plan/TestItineraryBuilder.java` — `flex(...)` and `carHail(...)` methods +
  their exclusive `ext.flex.*`/`ext.ridehailing.model.*` imports.
- `test/.../_support/geometry/Polygons.java` — `toGeoJson(...)` method + `java.util.Arrays`/
  `org.geojson.LngLatAlt` imports (`org.geojson`/geojson-jackson is not a pinned dependency and
  is unused by any surviving test).

### Authorized edit (precedent: `:otp-street`'s `StreetSummarizer.java`)
- `testFixtures/.../transit/service/NoopSiteResolver.java` — replaced
  `org.apache.commons.lang3.NotImplementedException` with `UnsupportedOperationException`
  (commons-lang3 is a forbidden dependency), 3 call sites.

## 9. Relocations
- `_support/geometry/Coordinates.java` — upstream `application/src/test/java`; relocated to
  `src/testFixtures/java` per the task brief (`SiteRepositoryTestBuilder` needs it).
- `transit/model/_data/{PatternTestModel,TransitRepositoryForTest,TripTimesForTest}.java` —
  upstream `application/src/test/java`; relocated to `src/testFixtures/java` because
  `TestStateBuilder.java` (also in `src/testFixtures/java`) needs `TransitRepositoryForTest`, and
  Gradle's `testFixtures` sourceSet cannot see `src/test/java` (the dependency direction is
  one-way: `test` sees `testFixtures`, not the reverse — unlike upstream's Maven build, which
  merges `test` and `test-fixtures` into one compilation scope via `build-helper-maven-plugin`).
- `LocalTimeParser.java` — upstream `application/src/test/java`; placed in `src/testFixtures/java`
  because `TransitTestEnvironment.java` needs it.
- `street/search/state/TestStateBuilder.java` — upstream
  `application/src/test-fixtures/java/org/opentripplanner/street/search/state/TestStateBuilder.java`;
  originally placed in `src/testFixtures/java` (see the note under section 6), then relocated to
  `src/test/java/org/opentripplanner/street/search/state/TestStateBuilder.java` because
  `:otp-street`'s testFixtures ships a different class of the same fully-qualified name, and
  publishing both from a project's own testFixtures jar caused a D8 duplicate-class error on the
  Android instrumentation APK; keeping this copy in `src/test/java` (which shadows the
  `:otp-street` fixture on the compile/test classpath but isn't itself published as a fixture)
  avoids the collision. Only `:otp-routing`'s own tests use it.
- `transfer/constrained/TransferTestData.java` — upstream `application/src/test-fixtures/java`;
  placed in `src/test/java` (not `src/testFixtures/java`) since only `src/test/java` consumers
  (`TransferPointMapTest`, `TransferServiceTest`) need it.
- `raptorlegacy/_data/**` and `test/support/TestTableParser.java` — upstream
  `application/src/test/java`; not matched by the Step 3 main-package-directory copy loop
  (`raptorlegacy` and `test.support` have no counterpart directory under `src/main/java`) but
  needed transitively by many surviving tests (`raptorlegacy/_data` in particular, by most of
  `routing/algorithm/raptoradapter/*`); copied into `src/test/java` as-is.

## 10. Main-tree additions made while porting fixtures/tests
47 files were added to `src/main/java` in this task — either a main class a fixture/test needed
that Task 4 hadn't reached, or (the `framework/transaction/*` and `framework/event/*` subtree,
20 files) the transactional-repository framework `TransitTestEnvironment.applyUpdate(...)` needs,
minus its Dagger wiring (`configure/TransactionModule.java`, `configure/StreetDomain.java`,
`configure/TransitDomain.java`, `TimetableSnapshotParameters.java` were *not* copied — nothing in
this slice's fixtures/tests calls `TransactionFactory.createUpdateManagerWithPeriodicCommits(...)`,
only `createRepositoryRegistry()`/`createUpdateManagerWithAtomicCommits(...)`, so the Dagger module
that wires the periodic-commit path was never needed). All 47 are verbatim copies except
`framework/error/WordList.java` (JDK 17, see section 8).

```
api/resource/WebMercatorTile.java
framework/error/WordList.java  [JDK17]
framework/event/DomainEvent.java
framework/event/EventHandler.java
framework/transaction/api/RepositoryHandle.java
framework/transaction/api/RepositoryLifecycle.java
framework/transaction/api/TransactionScope.java
framework/transaction/api/WriteContext.java
framework/transaction/internal/DefaultRepositoryHandle.java
framework/transaction/internal/DefaultRepositoryRegistry.java
framework/transaction/internal/DefaultTransactionScope.java
framework/transaction/internal/DefaultUpdateManager.java
framework/transaction/internal/DefaultWriteContext.java
framework/transaction/internal/HandlerEntry.java
framework/transaction/internal/PeriodicCommitScheduler.java
framework/transaction/internal/RepositorySnapshotCache.java
framework/transaction/internal/Transaction.java
framework/transaction/internal/TransactionalRepository.java
framework/transaction/internal/TransactionFactory.java
framework/transaction/internal/TransactionManager.java
framework/transaction/RepositoryRegistry.java
framework/transaction/UpdateManager.java
model/plan/legreference/LegReferenceSerializer.java
model/plan/legreference/LegReferenceType.java
model/ShapePoint.java
routing/algorithm/raptoradapter/transit/mappers/TimetableUpdateMapper.java
routing/api/request/preference/Relax.java
routing/api/request/via/PassThroughViaLocation.java
routing/impl/DelegatingTransitAlertServiceImpl.java
service/streetdetails/internal/DefaultStreetDetailsRepository.java
transfer/regular/internal/DefaultTransferRepository.java
transfer/regular/internal/TransferIndex.java
transit/model/framework/Result.java
transit/model/site/Pathway.java
transit/model/site/PathwayBuilder.java
transit/model/site/PathwayMode.java
transit/model/site/PathwayNode.java
transit/model/site/PathwayNodeBuilder.java
transit/model/timetable/booking/RoutingBookingInfo.java
transit/model/timetable/EstimatedTime.java
transit/model/timetable/RealTimeTripUpdate.java
transit/model/timetable/StopTimeToScheduledTripTimesMapper.java
transit/model/timetable/TripTimesFactory.java
transit/repository/DefaultTimetableRepository.java
transit/repository/TimetableRepository.java
transit/repository/TimetableRepositoryLifecycle.java
transit/service/PatternByServiceDatesFilter.java
```

## otp-server-ui addition: graph loading

`routing/graph/SerializedGraphObject.java` and `routing/graph/kryosupport/*` were added on top of
the original vendoring above, copied verbatim from the same upstream commit (`61a3af6798`) this
module's other files were cut from.
`WorldEnvelopeRepository`/`VehicleParkingRepository`/`OsmInfoGraphBuildRepository`/`StopConsolidationRepository`/`EmissionRepository`/`EmpiricalDelayRepository`/`FareServiceFactory`
are newly-written stubs, not copies — see this repo's own
`docs/superpowers/specs/2026-09-30-otp-server-ui-design.md` for why. All seven are empty marker
interfaces: `SerializedGraphObject` only ever holds them as constructor-injected fields and never
calls a method on them, so no behaviour needed to be stubbed in.

Compiling `SerializedGraphObject.java` surfaced several more unresolved dependencies beyond that
enumerated list, handled as follows:

- **`org/opentripplanner/graph_builder/issue/api/DataImportIssueSummary.java`** — copied verbatim,
  per plan, as a small data-holder class outside the excluded `graph_builder.*` ingestion
  proper. Its own small dependency closure (`DataImportIssueStore.java`, `DataImportIssue.java`,
  `NoopDataImportIssueStore.java`, all in the same `issue/api` package, ~155 lines combined) was
  copied verbatim too, since `DataImportIssueSummary`'s constructor and static import reference
  them directly and they in turn need nothing outside this package plus JTS's `Geometry`
  (already a dependency) and the already-vendored `framework/error/OtpError.java`.
- **`org/opentripplanner/datastore/api/DataSource.java`** — not named in the task brief; surfaced
  as an additional unresolved import. Real upstream `DataSource` is a generalized file/zip/cloud
  storage abstraction backing the graph-building/data-import pipeline, out of scope for this
  project the same way `graph_builder.*` is. Written as a new stub interface (not a copy)
  declaring only the methods `SerializedGraphObject` itself calls on it
  (`name()`/`path()`/`exists()`/`isWritable()`/`size()`/`asInputStream()`/`asOutputStream()`), with
  no bodies — nothing in this module ever constructs a `DataSource`, since graph loading here
  always goes through `SerializedGraphObject.load(File)`, not `load(DataSource)`.
- **`org/opentripplanner/kryo/UnmodifiableCollectionsSerializer.java`** — copied verbatim (needed
  by `KryoBuilder.java`; self-contained, only `java.util.*` and Kryo imports).
- **`org/opentripplanner/kryo/BuildConfigSerializer.java`** and
  **`org/opentripplanner/kryo/RouterConfigSerializer.java`** — newly-written stubs, not copies.
  The real upstream versions round-trip `BuildConfig`/`RouterConfig`'s underlying JSON config tree
  through `standalone.config.framework.file.ConfigFileLoader`, which doesn't exist here (the real
  config-parsing framework is out of scope — see the pre-existing `standalone.config` stubs from
  Task 2). Since this module's `BuildConfig`/`RouterConfig` are themselves empty no-arg-constructor
  stubs with no state, these serializers write nothing and reconstruct a fresh instance on read.

Two extra Gradle dependencies were needed beyond the brief's `com.esotericsoftware:kryo:5.6.2`,
because `KryoBuilder.java` (one of the 7 verbatim `kryosupport` files) also imports
`com.conveyal.kryo.*` and `de.javakaffee.kryoserializers.guava.*`:
`com.conveyal:kryo-tools` and `de.javakaffee:kryo-serializers:0.45`. Upstream's own `pom.xml` pins
`kryo-tools` to `1.6.0`, but that version's jar is compiled to class-file version 64 (Java 20),
which fails `otp-routing`'s `--release 17` compile ("bad class file ... should be 61.0"). Pinned to
`kryo-tools:1.5.0` instead (class-file version compatible with Java 17) — the only files this
module uses from it (`TIntArrayListSerializer`, `TIntIntHashMapSerializer`) are unchanged between
those releases. `org.objenesis.strategy.SerializingInstantiatorStrategy` (also used by
`KryoBuilder.java`) needed no separate dependency entry — it resolves transitively through Kryo's
own dependency on `org.objenesis:objenesis`.
