package fr.cs.aerospace.orekit.propagation;

import fr.cs.aerospace.orekit.attitudes.AttitudeKinematicsProvider;
import fr.cs.aerospace.orekit.errors.OrekitException;
import fr.cs.aerospace.orekit.frames.Frame;
import fr.cs.aerospace.orekit.orbits.EquinoctialParameters;
import fr.cs.aerospace.orekit.orbits.Orbit;
import fr.cs.aerospace.orekit.orbits.OrbitalParameters;
import fr.cs.aerospace.orekit.time.AbsoluteDate;

// FIXME JAVADOC and TEST
public abstract class FixedStepHandler {

  public FixedStepHandler() {
    mantissaFixedStepHandler = new mappingFixedStepHandler();
  }
  
  protected void initialize(AbsoluteDate date, AttitudeKinematicsProvider provider,
                              Frame frame, double mu) {
     initialDate = date;
     inertialFrame = frame;
     akProvider = provider;
     this.mu = mu;
  }
  
  protected org.spaceroots.mantissa.ode.FixedStepHandler getMantissaStepHandler() {
   return mantissaFixedStepHandler;
 }

  public abstract void handleStep(
   SpacecraftState currentState, boolean isLast);

  public abstract boolean requiresDenseOutput();

  public abstract void reset() ;
  
  AbsoluteDate initialDate;
  Frame inertialFrame;
  AttitudeKinematicsProvider akProvider;
  double mu;
  
  mappingFixedStepHandler mantissaFixedStepHandler;
  
  private class mappingFixedStepHandler implements org.spaceroots.mantissa.ode.FixedStepHandler {
    
    public void handleStep(double t, double[] y, boolean isLast) {
      OrbitalParameters op =
        new EquinoctialParameters(y[0], y[1], y[2], y[3], y[4], y[5],
                                  EquinoctialParameters.TRUE_LATITUDE_ARGUMENT,
                                  inertialFrame);
      AbsoluteDate current = new AbsoluteDate(initialDate, t);
      
      try {
        FixedStepHandler.this.handleStep(new SpacecraftState(new Orbit(current, op), y[6], 
                            akProvider.getAttitudeKinematics(current, op.getPVCoordinates(mu), inertialFrame)), isLast);
      } catch (OrekitException e) {
        throw new RuntimeException(e.getLocalizedMessage());
      }
 
    }

    public boolean requiresDenseOutput() {
      return FixedStepHandler.this.requiresDenseOutput();
    }

    public void reset() {
     FixedStepHandler.this.reset();
    }


    
  }
  
}
