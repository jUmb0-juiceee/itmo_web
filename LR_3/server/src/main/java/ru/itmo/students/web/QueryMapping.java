package ru.itmo.students.web;

import org.springframework.web.bind.annotation.RequestMapping;
import java.lang.annotation.*;

/** Spring 6 has no QUERY member in RequestMethod; WebConfiguration adds its condition. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@RequestMapping
public @interface QueryMapping { }
