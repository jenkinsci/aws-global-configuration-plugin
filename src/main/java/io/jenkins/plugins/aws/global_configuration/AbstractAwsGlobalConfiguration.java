/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.plugins.aws.global_configuration;

import java.util.logging.Level;
import java.util.logging.Logger;


import org.apache.commons.lang.StringUtils;

import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;

public abstract class AbstractAwsGlobalConfiguration extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(AbstractAwsGlobalConfiguration.class.getName());

    public AbstractAwsGlobalConfiguration() {
        load();
    }

    @Override 
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(AwsGlobalConfigurationCategory.class);
    }

    /**
     * it returns a different cause message based on exception type.
     *
     * @param t
     *            Throwable to process.
     * @return the proper cause message.
     */
    protected String processExceptionMessage(Throwable t) {
        LOGGER.log(Level.FINEST, t.getMessage(), t);

        String msg = t.getMessage();
        String className = t.getClass().getSimpleName();
        return className + ":" + StringUtils.defaultIfBlank(msg, "Unknown error");
    }

}
