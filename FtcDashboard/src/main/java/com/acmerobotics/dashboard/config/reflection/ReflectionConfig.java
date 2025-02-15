package com.acmerobotics.dashboard.config.reflection;

import android.util.Log;

import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.dashboard.config.variable.BasicVariable;
import com.acmerobotics.dashboard.config.variable.ConfigVariable;
import com.acmerobotics.dashboard.config.variable.CustomVariable;
import com.acmerobotics.dashboard.config.variable.VariableType;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

public class ReflectionConfig {
    private static final String TAG = "ReflectionConfig";

    private ReflectionConfig() {}

    public static CustomVariable scanForClasses(final Set<String> packageIgnorePrefixes) {
        final CustomVariable configRoot = new CustomVariable();

        ClasspathScanner scanner = new ClasspathScanner(new ClasspathScanner.Callback() {
            @Override
            public boolean shouldProcessClass(String className) {
                for (String prefix : packageIgnorePrefixes) {
                    if (className.startsWith(prefix)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void processClass(Class<?> configClass) {
                // I'm doing the check this way because AS's linter screams at me for using
                //  configClass.isAnnotationPresent(Config.class). It might technically be safer.
                //  If it's slower, it's on the scale of nanoseconds, so I don't care.
                //  ~ryleu

                // Grab the config annotation
                Config config = configClass.getAnnotation(Config.class);

                // If the annotation exists, and either ignoreDisabled is set or @Disabled is not
                //  present...
                if (config != null && (!configClass.isAnnotationPresent(Disabled.class)
                            || config.ignoreDisabled())) {
                    Log.i(TAG, "Config class: " + configClass.getName());

                    // Set the name to either the simple name of the class, or, if present,
                    //  @Config.value
                    String name = configClass.getSimpleName();
                    String altName = config.value();
                    if (!altName.isEmpty()) {
                        name = altName;
                    }

                    // Add the variable to the configuration section of the dash
                    configRoot.putVariable(name, createVariableFromClass(configClass));
                }
            }
        });
        scanner.scanClasspath();

        return configRoot;
    }

    public static CustomVariable createVariableFromClass(Class<?> configClass) {
        CustomVariable customVariable = new CustomVariable();

        for (Field field : configClass.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            customVariable.putVariable(field.getName(), createVariableFromField(field, null));
        }

        return customVariable;
    }

    private static ConfigVariable<?> createVariableFromField(Field field, Object parent) {
        Class<?> fieldClass = field.getType();
        VariableType type = VariableType.fromClass(fieldClass);
        switch (type) {
            case BOOLEAN:
            case INT:
            case DOUBLE:
            case STRING:
            case ENUM:
                return new BasicVariable<>(type, new FieldProvider<Boolean>(field, parent));
            case CUSTOM:
                CustomVariable customVariable = new CustomVariable();
                for (Field nestedField : fieldClass.getFields()) {
                    if (Modifier.isFinal(field.getModifiers())) {
                        continue;
                    }

                    String name = nestedField.getName();
                    try {
                        customVariable.putVariable(name,
                                createVariableFromField(nestedField, field.get(parent)));
                    } catch (IllegalAccessException e) {
                        Log.w(TAG, e);
                    }
                }

                return customVariable;
            default:
                throw new RuntimeException("Unsupported field type: " +
                        fieldClass.getName());
        }
    }
}
