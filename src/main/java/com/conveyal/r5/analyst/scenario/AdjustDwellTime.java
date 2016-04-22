package com.conveyal.r5.analyst.scenario;

import com.conveyal.r5.transit.TransportNetwork;
import com.conveyal.r5.transit.TripPattern;
import com.conveyal.r5.transit.TripSchedule;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adjust the dwell times on matched trips.
 * This could reasonably be combined with adjust-speed, but adjust-speed requires a more complicated hop-based
 * description of which stops will be affected. This modification type exists for simplicity from the user perspective.
 * Supply only one of dwellSecs or scale, and supply only routes or trips, but not both.
 */
public class AdjustDwellTime extends Modification {

    private static final Logger LOG = LoggerFactory.getLogger(AdjustDwellTime.class);

    public static final long serialVersionUID = 1L;

    /** The routes which should have dwell times changed. */
    public Set<String> routes;

    /** Trips which should have dwell times changed. */
    public Set<String> trips;

    /** Stops at which to change the dwell times. If not specified, all dwell times will be changed. */
    public Set<String> stops;

    /** New dwell time in seconds. */
    public int dwellSecs = -1;

    /** Multiplicative factor to stretch or shrink the dwell times. */
    public double scale = -1;

    /** The internal integer IDs for the stops to be adjusted, resolved once before the modification is applied. */
    private transient TIntSet intStops;

    @Override
    public String getType() {
        return "adjust-dwell-time";
    }

    @Override
    public boolean apply (TransportNetwork network) {
        if (stops != null) {
            intStops = new TIntHashSet();
            for (String stringStopId : stops) {
                int intStopId = network.transitLayer.indexForStopId.get(stringStopId);
                if (intStopId == -1) {
                    warnings.add("Could not find a stop to adjust with GTFS ID " + stringStopId);
                } else {
                    intStops.add(intStopId);
                }
            }
            LOG.info("Resolved stop IDs for removal. Strings {} resolved to integers {}.", stops, intStops);
        }
        // Not bitwise operator: non-short-circuit logical XOR.
        if (!((dwellSecs >= 0) ^ (scale >= 0))) {
            warnings.add("Dwell time or scaling factor must be specified, but not both.");
        }
        if (!((routes != null) ^ (trips != null))) {
            warnings.add("Routes or trips must be specified, but not both.");
        }
        if (warnings.size() > 0) {
            return true;
        }
        network.transitLayer.tripPatterns = network.transitLayer.tripPatterns.stream()
                .map(this::processTripPattern)
                .collect(Collectors.toList());
        return warnings.size() > 0;
    }

    private TripPattern processTripPattern (TripPattern originalPattern) {
        if (routes != null && !routes.contains(originalPattern.routeId)) {
            // This TripPattern is not on a route that has been chosen for adjustment.
            return originalPattern;
        }
        // Avoid unnecessary new lists and cloning when no trips in this pattern are affected.
        if (trips != null && originalPattern.tripSchedules.stream().noneMatch(s -> trips.contains(s.tripId))) {
            return originalPattern;
        }
        // Make a shallow protective copy of this TripPattern.
        TripPattern newPattern = originalPattern.clone();
        int nStops = newPattern.stops.length;
        newPattern.tripSchedules = new ArrayList<>();
        for (TripSchedule originalSchedule : originalPattern.tripSchedules) {
            if (trips != null && !trips.contains(originalSchedule.tripId)) {
                // This trip has not been chosen for adjustment.
                newPattern.tripSchedules.add(originalSchedule);
                continue;
            }
            TripSchedule newSchedule = originalSchedule.clone();
            newPattern.tripSchedules.add(newSchedule);
            newSchedule.arrivals = new int[nStops];
            newSchedule.departures = new int[nStops];
            // Use a floating-point number to avoid accumulating integer truncation error.
            double seconds = originalSchedule.arrivals[0];
            for (int s = 0; s < nStops; s++) {
                newSchedule.arrivals[s] = (int) Math.round(seconds);
                // use double here as well, continue to avoid truncation error
                // consider the case case where you're halving dwell times of 19 seconds; truncation error would build
                // up half a second per stop.
                double dwellTime = originalSchedule.departures[s] - originalSchedule.arrivals[s];
                if (stops == null || intStops.contains(newPattern.stops[s])) {
                    if (dwellSecs >= 0) {
                        dwellTime = dwellSecs;
                    } else {
                        dwellTime *= scale;
                    }
                }
                seconds += dwellTime;
                newSchedule.departures[s] = (int) Math.round(seconds);
                if (s < nStops - 1) {
                    // We are not at the last stop in the pattern, so compute and accumulate the following hop.
                    int rideTime = originalSchedule.arrivals[s + 1] - originalSchedule.departures[s];
                    seconds += rideTime;
                }
            }
        }
        return newPattern;
    }

}
