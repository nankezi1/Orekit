/* Copyright 2002-2012 CS Systèmes d'Information
 * Licensed to CS Systèmes d'Information (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.propagation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.orekit.attitudes.Attitude;
import org.orekit.attitudes.LofOffset;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.frames.Frame;
import org.orekit.frames.LOFType;
import org.orekit.frames.Transform;
import org.orekit.orbits.Orbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeInterpolable;
import org.orekit.time.TimeShiftable;
import org.orekit.time.TimeStamped;
import org.orekit.utils.HermiteInterpolator;
import org.orekit.utils.PVCoordinates;


/** This class is the representation of a complete state holding orbit, attitude
 * and mass information at a given date.
 *
 * <p>It contains an {@link Orbit orbital state} at a current
 * {@link AbsoluteDate} both handled by an {@link Orbit}, plus the current
 * mass and attitude. Orbit and state are guaranteed to be consistent in terms
 * of date and reference frame.
 * </p>
 * <p>
 * The state can be slightly shifted to close dates. This shift is based on
 * a simple keplerian model for orbit, a linear extrapolation for attitude
 * taking the spin rate into account and no mass change. It is <em>not</em>
 * intended as a replacement for proper orbit and attitude propagation but
 * should be sufficient for either small time shifts or coarse accuracy.
 * </p>
 * <p>
 * The instance <code>SpacecraftState</code> is guaranteed to be immutable.
 * </p>
 * @see org.orekit.propagation.numerical.NumericalPropagator
 * @author Fabien Maussion
 * @author V&eacute;ronique Pommier-Maurussane
 * @author Luc Maisonobe
 */
public class SpacecraftState
    implements TimeStamped, TimeShiftable<SpacecraftState>, TimeInterpolable<SpacecraftState>, Serializable {

    /** Serializable UID. */
    private static final long serialVersionUID = 3141803003950085500L;

    /** Default mass. */
    private static final double DEFAULT_MASS = 1000.0;

    /** Orbital state. */
    private final Orbit orbit;

    /** Attitude. */
    private final Attitude attitude;

    /** Current mass (kg). */
    private final double mass;

    /** Build a spacecraft state from orbit only.
     * <p>Attitude and mass are set to unspecified non-null arbitrary values.</p>
     * @param orbit the orbit
     * @exception OrekitException if default attitude cannot be computed
     */
    public SpacecraftState(final Orbit orbit)
        throws OrekitException {
        this.orbit    = orbit;
        this.attitude = new LofOffset(orbit.getFrame(), LOFType.VVLH).getAttitude(orbit, orbit.getDate(), orbit.getFrame());
        this.mass     = DEFAULT_MASS;
    }

    /** Build a spacecraft state from orbit and attitude provider.
     * <p>Mass is set to an unspecified non-null arbitrary value.</p>
     * @param orbit the orbit
     * @param attitude attitude
     * @exception IllegalArgumentException if orbit and attitude dates
     * or frames are not equal
     */
    public SpacecraftState(final Orbit orbit, final Attitude attitude)
        throws IllegalArgumentException {
        checkConsistency(orbit, attitude);
        this.orbit    = orbit;
        this.attitude = attitude;
        this.mass     = DEFAULT_MASS;
    }

    /** Create a new instance from orbit and mass.
     * <p>Attitude law is set to an unspecified default attitude.</p>
     * @param orbit the orbit
     * @param mass the mass (kg)
     * @exception OrekitException if default attitude cannot be computed
     */
    public SpacecraftState(final Orbit orbit, final double mass)
        throws OrekitException {
        this.orbit    = orbit;
        this.attitude = new LofOffset(orbit.getFrame(), LOFType.VVLH).getAttitude(orbit, orbit.getDate(), orbit.getFrame());
        this.mass     = mass;
    }

    /** Build a spacecraft state from orbit, attitude provider and mass.
     * @param orbit the orbit
     * @param attitude attitude
     * @param mass the mass (kg)
     * @exception IllegalArgumentException if orbit and attitude dates
     * or frames are not equal
     */
    public SpacecraftState(final Orbit orbit, final Attitude attitude, final double mass)
        throws IllegalArgumentException {
        checkConsistency(orbit, attitude);
        this.orbit    = orbit;
        this.attitude = attitude;
        this.mass     = mass;
    }

    /** Check orbit and attitude dates are equal.
     * @param orbit the orbit
     * @param attitude attitude
     * @exception IllegalArgumentException if orbit and attitude dates
     * are not equal
     */
    private static void checkConsistency(final Orbit orbit, final Attitude attitude)
        throws IllegalArgumentException {
        if (!orbit.getDate().equals(attitude.getDate())) {
            throw OrekitException.createIllegalArgumentException(
                  OrekitMessages.ORBIT_AND_ATTITUDE_DATES_MISMATCH,
                  orbit.getDate(), attitude.getDate());
        }
        if (orbit.getFrame() != attitude.getReferenceFrame()) {
            throw OrekitException.createIllegalArgumentException(
                  OrekitMessages.FRAMES_MISMATCH,
                  orbit.getFrame().getName(), attitude.getReferenceFrame().getName());
        }
    }

    /** Get a time-shifted state.
     * <p>
     * The state can be slightly shifted to close dates. This shift is based on
     * a simple keplerian model for orbit, a linear extrapolation for attitude
     * taking the spin rate into account and no mass change. It is <em>not</em>
     * intended as a replacement for proper orbit and attitude propagation but
     * should be sufficient for small time shifts or coarse accuracy.
     * </p>
     * <p>
     * As a rough order of magnitude, the following table shows the interpolation
     * errors obtained between this simple shift method and an {@link
     * org.orekit.propagation.analytical.EcksteinHechlerPropagator Eckstein-Heschler
     * propagator} for an 800km altitude nearly circular polar Earth orbit with
     * {@link org.orekit.attitudes.BodyCenterPointing body center pointing}. Beware
     * that these results may be different for other orbits.
     * </p>
     * <table border="1" cellpadding="5">
     * <tr bgcolor="#ccccff"><th>interpolation time (s)</th>
     * <th>position error (m)</th><th>velocity error (m/s)</th>
     * <th>attitude error (&deg;)</th></tr>
     * <tr><td bgcolor="#eeeeff"> 60</td><td>  20</td><td>1</td><td>0.001</td></tr>
     * <tr><td bgcolor="#eeeeff">120</td><td> 100</td><td>2</td><td>0.002</td></tr>
     * <tr><td bgcolor="#eeeeff">300</td><td> 600</td><td>4</td><td>0.005</td></tr>
     * <tr><td bgcolor="#eeeeff">600</td><td>2000</td><td>6</td><td>0.008</td></tr>
     * <tr><td bgcolor="#eeeeff">900</td><td>4000</td><td>6</td><td>0.010</td></tr>
     * </table>
     * @param dt time shift in seconds
     * @return a new state, shifted with respect to the instance (which is immutable)
     * except for the mass which stay unchanged
     */
    public SpacecraftState shiftedBy(final double dt) {
        return new SpacecraftState(orbit.shiftedBy(dt), attitude.shiftedBy(dt), mass);
    }

    /** {@inheritDoc}
     * <p>
     * As this implementation of interpolation is polynomial, it should be used only
     * with small samples (about 10-20 points) in order to avoid <a
     * href="http://en.wikipedia.org/wiki/Runge%27s_phenomenon">Runge's phenomenon</a>
     * and numerical problems (including NaN appearing).
     * </p>
     */
    public SpacecraftState interpolate(final AbsoluteDate date,
                                       final Collection<SpacecraftState> sample) {
        final List<Orbit> orbits = new ArrayList<Orbit>(sample.size());
        final List<Attitude> attitudes = new ArrayList<Attitude>(sample.size());
        final HermiteInterpolator massInterpolator = new HermiteInterpolator();
        for (final SpacecraftState state : sample) {
            orbits.add(state.getOrbit());
            attitudes.add(state.getAttitude());
            massInterpolator.addSamplePoint(state.getDate().durationFrom(date),
                                            new double[] {
                                                state.getMass()
                                            });
        }
        return new SpacecraftState(orbit.interpolate(date, orbits),
                                   attitude.interpolate(date, attitudes),
                                   massInterpolator.value(0)[0]);
    }

    /** Gets the current orbit.
     * @return the orbit
     */
    public Orbit getOrbit() {
        return orbit;
    }

    /** Get the date.
     * @return date
     */
    public AbsoluteDate getDate() {
        return orbit.getDate();
    }

    /** Get the inertial frame.
     * @return the frame
     */
    public Frame getFrame() {
        return orbit.getFrame();
    }

    /** Compute the transform from orbite/attitude reference frame to spacecraft frame.
     * <p>The spacecraft frame origin is at the point defined by the orbit,
     * and its orientation is defined by the attitude.</p>
     * @return transform from specified frame to current spacecraft frame
     */
    public Transform toTransform() {
        final AbsoluteDate date = orbit.getDate();
        return new Transform(date,
                             new Transform(date, orbit.getPVCoordinates().negate()),
                             new Transform(date, attitude.getOrientation()));
    }

    /** Get the central attraction coefficient.
     * @return mu central attraction coefficient (m^3/s^2)
     */
    public double getMu() {
        return orbit.getMu();
    }

    /** Get the keplerian period.
     * <p>The keplerian period is computed directly from semi major axis
     * and central acceleration constant.</p>
     * @return keplerian period in seconds
     */
    public double getKeplerianPeriod() {
        return orbit.getKeplerianPeriod();
    }

    /** Get the keplerian mean motion.
     * <p>The keplerian mean motion is computed directly from semi major axis
     * and central acceleration constant.</p>
     * @return keplerian mean motion in radians per second
     */
    public double getKeplerianMeanMotion() {
        return orbit.getKeplerianMeanMotion();
    }

    /** Get the semi-major axis.
     * @return semi-major axis (m)
     */
    public double getA() {
        return orbit.getA();
    }

    /** Get the first component of the eccentricity vector (as per equinoctial parameters).
     * @return e cos(&omega; + &Omega;), first component of eccentricity vector
     * @see #getE()
     */
    public double getEquinoctialEx() {
        return orbit.getEquinoctialEx();
    }

    /** Get the second component of the eccentricity vector (as per equinoctial parameters).
     * @return e sin(&omega; + &Omega;), second component of the eccentricity vector
     * @see #getE()
     */
    public double getEquinoctialEy() {
        return orbit.getEquinoctialEy();
    }

    /** Get the first component of the inclination vector (as per equinoctial parameters).
     * @return tan(i/2) cos(&Omega;), first component of the inclination vector
     * @see #getI()
     */
    public double getHx() {
        return orbit.getHx();
    }

    /** Get the second component of the inclination vector (as per equinoctial parameters).
     * @return tan(i/2) sin(&Omega;), second component of the inclination vector
     * @see #getI()
     */
    public double getHy() {
        return orbit.getHy();
    }

    /** Get the true latitude argument (as per equinoctial parameters).
     * @return v + &omega; + &Omega; true latitude argument (rad)
     * @see #getLE()
     * @see #getLM()
     */
    public double getLv() {
        return orbit.getLv();
    }

    /** Get the eccentric latitude argument (as per equinoctial parameters).
     * @return E + &omega; + &Omega; eccentric latitude argument (rad)
     * @see #getLv()
     * @see #getLM()
     */
    public double getLE() {
        return orbit.getLE();
    }

    /** Get the mean latitude argument (as per equinoctial parameters).
     * @return M + &omega; + &Omega; mean latitude argument (rad)
     * @see #getLv()
     * @see #getLE()
     */
    public double getLM() {
        return orbit.getLM();
    }

    // Additional orbital elements

    /** Get the eccentricity.
     * @return eccentricity
     * @see #getEquinoctialEx()
     * @see #getEquinoctialEy()
     */
    public double getE() {
        return orbit.getE();
    }

    /** Get the inclination.
     * @return inclination (rad)
     * @see #getHx()
     * @see #getHy()
     */
    public double getI() {
        return orbit.getI();
    }

    /** Get the {@link PVCoordinates} in orbit definition frame.
     * Compute the position and velocity of the satellite. This method caches its
     * results, and recompute them only when the method is called with a new value
     * for mu. The result is provided as a reference to the internally cached
     * {@link PVCoordinates}, so the caller is responsible to copy it in a separate
     * {@link PVCoordinates} if it needs to keep the value for a while.
     * @return pvCoordinates in orbit definition frame
     */
    public PVCoordinates getPVCoordinates() {
        return orbit.getPVCoordinates();
    }

    /** Get the {@link PVCoordinates} in given output frame.
     * Compute the position and velocity of the satellite. This method caches its
     * results, and recompute them only when the method is called with a new value
     * for mu. The result is provided as a reference to the internally cached
     * {@link PVCoordinates}, so the caller is responsible to copy it in a separate
     * {@link PVCoordinates} if it needs to keep the value for a while.
     * @param outputFrame frame in which coordinates should be defined
     * @return pvCoordinates in orbit definition frame
     * @exception OrekitException if the transformation between frames cannot be computed
     */
    public PVCoordinates getPVCoordinates(final Frame outputFrame)
        throws OrekitException {
        return orbit.getPVCoordinates(outputFrame);
    }

    /** Get the attitude.
     * @return the attitude.
     */
    public Attitude getAttitude() {
        return attitude;
    }

    /** Gets the current mass.
     * @return the mass (kg)
     */
    public double getMass() {
        return mass;
    }

}
