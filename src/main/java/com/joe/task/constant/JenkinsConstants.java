package com.joe.task.constant;

import com.google.common.collect.Lists;

import java.util.List;

public class JenkinsConstants
{
    public static final String ENV_DEVELOP   = "develop";
    public static final String ENV_CANDIDATE = "candidate";

    public static final List<String> ENV_LIST = Lists.newArrayList(ENV_DEVELOP, ENV_CANDIDATE);
}
