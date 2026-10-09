package com.polaris.ai.modelcenter.schema.options;

import java.util.List;

/** 仅允许注册过的后端 Resolver 产生动态选项。 */
public interface SchemaOptionsResolver {

    String code();

    List<SchemaOption> resolve(OptionsResolveContext context);
}
