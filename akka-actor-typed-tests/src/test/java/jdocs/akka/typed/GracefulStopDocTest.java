/**
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package jdocs.akka.typed;

//#imports

import java.util.concurrent.TimeUnit;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.Behaviors;

//#imports

public class GracefulStopDocTest {

  //#master-actor

  public abstract static class JobControl {
    // no instances of this class, it's only a name space for messages
    // and static methods
    private JobControl() {
    }

    static interface JobControlLanguage {
    }

    public static final class SpawnJob implements JobControlLanguage {
      public final String name;

      public SpawnJob(String name) {
        this.name = name;
      }
    }

    public static final class GracefulShutdown implements JobControlLanguage {

      public GracefulShutdown() {
      }
    }

    public static final Behavior<JobControlLanguage> mcpa = Behaviors.immutable(JobControlLanguage.class)
        .onMessage(SpawnJob.class, (ctx, msg) -> {
          ctx.getSystem().log().info("Spawning job {}!", msg.name);
          ctx.spawn(Job.job(msg.name), msg.name);
          return Behaviors.same();
        })
        .onSignal(PostStop.class, (ctx, signal) -> {
          ctx.getSystem().log().info("Master Control Programme stopped");
          return Behaviors.same();
        })
        .onMessage(GracefulShutdown.class, (ctx, msg) -> {
          ctx.getSystem().log().info("Initiating graceful shutdown...");

          // perform graceful stop, executing cleanup before final system termination
          // behavior executing cleanup is passed as a parameter to Actor.stopped
          return Behaviors.stopped(Behaviors.onSignal((context, PostStop) -> {
            context.getSystem().log().info("Cleanup!");
            return Behaviors.same();
          }));
        })
        .onSignal(PostStop.class, (ctx, signal) -> {
          ctx.getSystem().log().info("Master Control Programme stopped");
          return Behaviors.same();
        })
        .build();
  }
  //#master-actor

  public static void main(String[] args) throws Exception {
    //#graceful-shutdown

    final ActorSystem<JobControl.JobControlLanguage> system =
        ActorSystem.create(JobControl.mcpa, "B6700");

    system.tell(new JobControl.SpawnJob("a"));
    system.tell(new JobControl.SpawnJob("b"));

    // sleep here to allow time for the new actors to be started
    Thread.sleep(100);

    system.tell(new JobControl.GracefulShutdown());

    system.getWhenTerminated().toCompletableFuture().get(3, TimeUnit.SECONDS);
    //#graceful-shutdown
  }

  //#worker-actor

  public static class Job {
    public static Behavior<JobControl.JobControlLanguage> job(String name) {
      return Behaviors.onSignal((ctx, PostStop) -> {
        ctx.getSystem().log().info("Worker {} stopped", name);
        return Behaviors.same();
      });

    }
  }
  //#worker-actor
}
