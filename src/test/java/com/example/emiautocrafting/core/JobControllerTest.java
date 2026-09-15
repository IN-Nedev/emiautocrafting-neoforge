// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JobControllerTest {
    static class Port implements JobController.Port<String> {
        boolean valid=true, done=false, fail=false, timeout=false; int sent=0; int plans=0;
        JobController.Confirmation confirmation=JobController.Confirmation.PENDING;
        public boolean valid(){return valid;}
        public JobController.Decision<String> plan(){plans++; return done?JobController.Decision.completedResult():JobController.Decision.ready("sticks");}
        public void dispatch(String s){sent++;if(fail) throw new IllegalStateException("rejected");}
        public JobController.Confirmation confirm(String s){return confirmation;}
        public void timeout(){timeout=true;}
        public String describe(String s){return s;}
        public void error(Exception e){}
    }
    @Test void noDuplicateWithLatencyOrRepeatedStart() {
        var c=new JobController<String>(); var p=new Port(); c.start(false,0);
        for(int i=0;i<50;i++){c.tick(i,100,4,p);assertFalse(c.start(false,i));}
        assertEquals(1,p.sent);assertEquals(JobController.State.WAITING,c.state());
    }
    @Test void onlyConfirmationAllowsReplanAndPacing() {
        var c=new JobController<String>();var p=new Port();c.start(false,0);c.tick(0,100,4,p);c.tick(1,100,4,p);
        p.confirmation=JobController.Confirmation.CONFIRMED;c.tick(2,100,4,p);c.tick(3,100,4,p);assertEquals(1,p.plans);
        p.done=true;c.tick(6,100,4,p);assertEquals(JobController.State.COMPLETED,c.state());assertEquals(1,p.sent);
    }
    @Test void timeoutStopsWithoutRetry() {
        var c=new JobController<String>();var p=new Port();c.start(false,0);
        for(int i=0;i<100;i++)c.tick(i,20,2,p);
        assertTrue(p.timeout);assertEquals(1,p.sent);assertEquals(JobController.State.BLOCKED,c.state());
    }
    @Test void timeoutRetainsTheSpecificSynchronizationReason() {
        var c=new JobController<String>();
        var p=new Port() { public String waitingMessage(){return "Server snapshot differs from displayed slot 379";} };
        c.start(false,0);c.tick(0,20,0,p);c.tick(20,20,0,p);
        assertEquals(JobController.State.BLOCKED,c.state());
        assertTrue(c.message().contains("displayed slot 379"));assertEquals(1,p.sent);
    }
    @Test void closingGuiStopsFutureDispatch() {
        var c=new JobController<String>();var p=new Port();c.start(false,0);p.valid=false;c.tick(0,100,2,p);
        assertEquals(JobController.State.CANCELLED,c.state());assertEquals(0,p.sent);
    }
    @Test void zeroDelayDispatchesOnlyOncePerTickAndStillWaitsForServer() {
        var c=new JobController<String>();var p=new Port();c.start(false,0);
        c.tick(0,100,0,p);assertEquals(1,p.sent);
        for(int t=1;t<20;t++)c.tick(t,100,0,p);
        assertEquals(1,p.sent);
        p.confirmation=JobController.Confirmation.CONFIRMED;
        c.tick(20,100,0,p);assertEquals(2,p.sent);
        p.confirmation=JobController.Confirmation.PENDING;
        c.tick(21,100,0,p);assertEquals(2,p.sent);
        p.valid=false;c.tick(22,100,0,p);assertEquals(2,p.sent);assertEquals(JobController.State.CANCELLED,c.state());
    }
    @Test void exceptionDoesNotLeaveAnActiveLock() {
        var c=new JobController<String>();var p=new Port();p.fail=true;c.start(false,0);c.tick(0,100,2,p);c.tick(1,100,2,p);
        assertEquals(JobController.State.FAILED,c.state());assertFalse(c.active());assertTrue(c.start(false,3));
    }
    @Test void rejectedCraftCannotComplete() {
        var c=new JobController<String>();var p=new Port();c.start(false,0);c.tick(0,100,2,p);c.tick(1,100,2,p);
        p.confirmation=JobController.Confirmation.REJECTED;c.tick(2,100,2,p);assertEquals(JobController.State.BLOCKED,c.state());
    }
    @Test void cancellationRetainsPendingOperationWithoutRepeatingIt() {
        var c=new JobController<String>();var p=new Port();c.start(false,0);c.tick(0,100,2,p);c.tick(1,100,2,p);
        c.cancel("Cancelled");p.confirmation=JobController.Confirmation.CONFIRMED;c.tick(2,100,2,p);
        assertEquals(JobController.State.CANCELLED,c.state());assertEquals(1,p.sent);
    }
    @Test void singleStepStopsAfterOneConfirmedCraft() {
        var c=new JobController<String>();var p=new Port();c.start(true,0);c.tick(0,100,2,p);c.tick(1,100,2,p);
        p.confirmation=JobController.Confirmation.CONFIRMED;for(int i=2;i<10;i++)c.tick(i,100,2,p);
        assertEquals(JobController.State.COMPLETED,c.state());assertEquals(1,p.sent);
    }
}
