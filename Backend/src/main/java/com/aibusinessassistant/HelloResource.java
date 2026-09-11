package com.aibusinessassistant;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path("/hello")
@Produces(MediaType.TEXT_PLAIN)
@RequiredArgsConstructor
public class HelloResource {

    private final GreetingService greetingService;

    @GET
    public String hello() {
        return "Hello World";
    }

    @GET
    @Path("/ai/{name}")
    public String aiHello(@PathParam("name") String name) {
        return greetingService.greet(name);
    }
}
