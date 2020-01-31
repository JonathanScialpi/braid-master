package io.bluebank.braid.core.synth;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.*;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.core.util.Constants;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.media.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import static io.swagger.v3.core.util.RefUtils.constructRef;

public class ModelResolverFunctions {

  final ObjectMapper _mapper;

  public ModelResolverFunctions(ObjectMapper mapper) {
    this._mapper = mapper;
  }

  /*
   * The functions below this comment are unaltered copy-and-paste from the master branch
   * -- which was version d17f81c7e --
   * of ModelResolver.java in https://github.com/swagger-api/swagger-core
   */

  protected String resolveDescription(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && !"".equals(schema.description())) {
      return schema.description();
    }
    return null;
  }

  protected String resolveTitle(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && StringUtils.isNotBlank(schema.title())) {
      return schema.title();
    }
    return null;
  }

  protected String resolveFormat(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && StringUtils.isNotBlank(schema.format())) {
      return schema.format();
    }
    return null;
  }

  protected String resolveDefaultValue(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null) {
      if(!schema.defaultValue().isEmpty()) {
        return schema.defaultValue();
      }
    }
    if(a == null) {
      return null;
    }
    XmlElement elem = a.getAnnotation(XmlElement.class);
    if(elem == null) {
      if(annotations != null) {
        for(Annotation ann : annotations) {
          if(ann instanceof XmlElement) {
            elem = (XmlElement)ann;
            break;
          }
        }
      }
    }
    if(elem != null) {
      if(!elem.defaultValue().isEmpty() && !"\u0000".equals(elem.defaultValue())) {
        return elem.defaultValue();
      }
    }
    return null;
  }

  protected Object resolveExample(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {

    if(schema != null) {
      if(!schema.example().isEmpty()) {
        try {
          return Json.mapper().readTree(schema.example());
        } catch(IOException e) {
          return schema.example();
        }
      }
    }

    return null;
  }

  protected io.swagger.v3.oas.annotations.media.Schema.AccessMode resolveAccessMode(BeanPropertyDefinition propDef, JavaType type, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && !schema.accessMode().equals(io.swagger.v3.oas.annotations.media.Schema.AccessMode.AUTO)) {
      return schema.accessMode();
    } else if(schema != null && schema.readOnly()) {
      return io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY;
    } else if(schema != null && schema.writeOnly()) {
      return io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY;
    }

    if(propDef == null) {
      return null;
    }
    JsonProperty.Access access = null;
    if(propDef instanceof POJOPropertyBuilder) {
      access = ((POJOPropertyBuilder)propDef).findAccess();
    }
    boolean hasGetter = propDef.hasGetter();
    boolean hasSetter = propDef.hasSetter();
    boolean hasConstructorParameter = propDef.hasConstructorParameter();
    boolean hasField = propDef.hasField();

    if(access == null) {
      final BeanDescription beanDesc = _mapper.getDeserializationConfig().introspect(type);
      List<BeanPropertyDefinition> properties = beanDesc.findProperties();
      for(BeanPropertyDefinition prop : properties) {
        if(StringUtils.isNotBlank(prop.getInternalName()) && prop.getInternalName().equals(propDef.getInternalName())) {
          if(prop instanceof POJOPropertyBuilder) {
            access = ((POJOPropertyBuilder)prop).findAccess();
          }
          hasGetter = hasGetter || prop.hasGetter();
          hasSetter = hasSetter || prop.hasSetter();
          hasConstructorParameter = hasConstructorParameter || prop.hasConstructorParameter();
          hasField = hasField || prop.hasField();
          break;
        }
      }
    }
    if(access == null) {
      if(!hasGetter && !hasField && (hasConstructorParameter || hasSetter)) {
        return io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY;
      }
      return null;
    } else {
      switch(access) {
        case AUTO:
          return io.swagger.v3.oas.annotations.media.Schema.AccessMode.AUTO;
        case READ_ONLY:
          return io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY;
        case READ_WRITE:
          return io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_WRITE;
        case WRITE_ONLY:
          return io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY;
        default:
          return io.swagger.v3.oas.annotations.media.Schema.AccessMode.AUTO;
      }
    }
  }

  protected Boolean resolveReadOnly(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && schema.accessMode().equals(io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)) {
      return true;
    } else if(schema != null && schema.accessMode().equals(io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY)) {
      return null;
    } else if(schema != null && schema.accessMode().equals(io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_WRITE)) {
      return null;
    } else if(schema != null && schema.readOnly()) {
      return schema.readOnly();
    }
    return null;
  }

  protected Boolean resolveNullable(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && schema.nullable()) {
      return schema.nullable();
    }
    return null;
  }

  protected BigDecimal resolveMultipleOf(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && schema.multipleOf() != 0) {
      return new BigDecimal(schema.multipleOf());
    }
    return null;
  }

  protected Integer resolveMaxLength(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && schema.maxLength() != Integer.MAX_VALUE && schema.maxLength() > 0) {
      return schema.maxLength();
    }
    return null;
  }

  protected Integer resolveMinLength(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && schema.minLength() > 0) {
      return schema.minLength();
    }
    return null;
  }

  protected BigDecimal resolveMinimum(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && NumberUtils.isNumber(schema.minimum())) {
      String filteredMinimum = schema.minimum().replaceAll(Constants.COMMA, StringUtils.EMPTY);
      return new BigDecimal(filteredMinimum);
    }
    return null;
  }

  protected BigDecimal resolveMaximum(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && NumberUtils.isNumber(schema.maximum())) {
      String filteredMaximum = schema.maximum().replaceAll(Constants.COMMA, StringUtils.EMPTY);
      return new BigDecimal(filteredMaximum);
    }
    return null;
  }

  protected Boolean resolveExclusiveMinimum(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && schema.exclusiveMinimum()) {
      return schema.exclusiveMinimum();
    }
    return null;
  }

  protected Boolean resolveExclusiveMaximum(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && schema.exclusiveMaximum()) {
      return schema.exclusiveMaximum();
    }
    return null;
  }

  protected String resolvePattern(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && StringUtils.isNotBlank(schema.pattern())) {
      return schema.pattern();
    }
    return null;
  }

  protected Integer resolveMinProperties(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && schema.minProperties() > 0) {
      return schema.minProperties();
    }
    return null;
  }

  protected Integer resolveMaxProperties(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && schema.maxProperties() > 0) {
      return schema.maxProperties();
    }
    return null;
  }

  protected List<String> resolveRequiredProperties(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null &&
         schema.requiredProperties() != null &&
         schema.requiredProperties().length > 0 &&
         StringUtils.isNotBlank(schema.requiredProperties()[0])) {

      return Arrays.asList(schema.requiredProperties());
    }
    return null;
  }

  protected Boolean resolveWriteOnly(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && schema.accessMode().equals(io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)) {
      return null;
    } else if(schema != null && schema.accessMode().equals(io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY)) {
      return true;
    } else if(schema != null && schema.accessMode().equals(io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_WRITE)) {
      return null;
    } else if(schema != null && schema.writeOnly()) {
      return schema.writeOnly();
    }
    return null;
  }

  protected ExternalDocumentation resolveExternalDocumentation(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {

    ExternalDocumentation external = null;
    if(a != null) {
      io.swagger.v3.oas.annotations.ExternalDocumentation externalDocumentation = a.getAnnotation(io.swagger.v3.oas.annotations.ExternalDocumentation.class);
      external = resolveExternalDocumentation(externalDocumentation);
    }

    if(external == null) {
      if(schema != null) {
        external = resolveExternalDocumentation(schema.externalDocs());
      }
    }
    return external;
  }

  protected ExternalDocumentation resolveExternalDocumentation(io.swagger.v3.oas.annotations.ExternalDocumentation externalDocumentation) {

    if(externalDocumentation == null) {
      return null;
    }
    boolean isEmpty = true;
    ExternalDocumentation external = new ExternalDocumentation();
    if(StringUtils.isNotBlank(externalDocumentation.description())) {
      isEmpty = false;
      external.setDescription(externalDocumentation.description());
    }
    if(StringUtils.isNotBlank(externalDocumentation.url())) {
      isEmpty = false;
      external.setUrl(externalDocumentation.url());
    }
    if(isEmpty) {
      return null;
    }
    return external;
  }

  protected Boolean resolveDeprecated(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null && schema.deprecated()) {
      return schema.deprecated();
    }
    return null;
  }

  protected List<String> resolveAllowableValues(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null &&
         schema.allowableValues() != null &&
         schema.allowableValues().length > 0) {
      return Arrays.asList(schema.allowableValues());
    }
    return null;
  }

  protected Map<String, Object> resolveExtensions(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    if(schema != null &&
         schema.extensions() != null &&
         schema.extensions().length > 0) {
      return AnnotationsUtils.getExtensions(schema.extensions());
    }
    return null;
  }

  protected void resolveDiscriminatorProperty(JavaType type, ModelConverterContext context, Schema model) {
    // add JsonTypeInfo.property if not member of bean
    JsonTypeInfo typeInfo = type.getRawClass().getDeclaredAnnotation(JsonTypeInfo.class);
    if(typeInfo != null) {
      String typeInfoProp = typeInfo.property();
      if(StringUtils.isNotBlank(typeInfoProp)) {
        Schema modelToUpdate = model;
        if(StringUtils.isNotBlank(model.get$ref())) {
          modelToUpdate = context.getDefinedModels().get(model.get$ref().substring(21));
        }
        if(modelToUpdate.getProperties() == null || !modelToUpdate.getProperties().keySet().contains(typeInfoProp)) {
          Schema discriminatorSchema = new StringSchema().name(typeInfoProp);
          modelToUpdate.addProperties(typeInfoProp, discriminatorSchema);
          if(modelToUpdate.getRequired() == null || !modelToUpdate.getRequired().contains(typeInfoProp)) {
            modelToUpdate.addRequiredItem(typeInfoProp);
          }
        }
      }
    }
  }

  protected Discriminator resolveDiscriminator(JavaType type, ModelConverterContext context) {

    io.swagger.v3.oas.annotations.media.Schema declaredSchemaAnnotation = AnnotationsUtils.getSchemaDeclaredAnnotation(type.getRawClass());

    String disc = (declaredSchemaAnnotation == null) ? "" : declaredSchemaAnnotation.discriminatorProperty();

    if(disc.isEmpty()) {
      // longer method would involve AnnotationIntrospector.findTypeResolver(...) but:
      JsonTypeInfo typeInfo = type.getRawClass().getDeclaredAnnotation(JsonTypeInfo.class);
      if(typeInfo != null) {
        disc = typeInfo.property();
      }
    }
    if(!disc.isEmpty()) {
      Discriminator discriminator = new Discriminator()
                                      .propertyName(disc);
      if(declaredSchemaAnnotation != null) {
        DiscriminatorMapping mappings[] = declaredSchemaAnnotation.discriminatorMapping();
        if(mappings != null && mappings.length > 0) {
          for(DiscriminatorMapping mapping : mappings) {
            if(!mapping.value().isEmpty() && !mapping.schema().equals(Void.class)) {
              discriminator.mapping(mapping.value(), constructRef(context.resolve(new AnnotatedType().type(mapping.schema())).getName()));
            }
          }
        }
      }

      return discriminator;
    }
    return null;
  }

  protected XML resolveXml(Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schema) {
    // if XmlRootElement annotation, construct an Xml object and attach it to the model
    XmlRootElement rootAnnotation = null;
    if(a != null) {
      rootAnnotation = a.getAnnotation(XmlRootElement.class);
    }
    if(rootAnnotation == null) {
      if(annotations != null) {
        for(Annotation ann : annotations) {
          if(ann instanceof XmlRootElement) {
            rootAnnotation = (XmlRootElement)ann;
            break;
          }
        }
      }
    }
    if(rootAnnotation != null && !"".equals(rootAnnotation.name()) && !"##default".equals(rootAnnotation.name())) {
      XML xml = new XML().name(rootAnnotation.name());
      if(rootAnnotation.namespace() != null && !"".equals(rootAnnotation.namespace()) && !"##default".equals(rootAnnotation.namespace())) {
        xml.namespace(rootAnnotation.namespace());
      }
      return xml;
    }
    return null;
  }

  protected Integer resolveMinItems(AnnotatedType a, io.swagger.v3.oas.annotations.media.ArraySchema arraySchema) {
    if(arraySchema != null) {
      if(arraySchema.minItems() < Integer.MAX_VALUE) {
        return arraySchema.minItems();
      }
    }
    return null;
  }

  protected Integer resolveMaxItems(AnnotatedType a, io.swagger.v3.oas.annotations.media.ArraySchema arraySchema) {
    if(arraySchema != null) {
      if(arraySchema.maxItems() > 0) {
        return arraySchema.maxItems();
      }
    }
    return null;
  }

  protected Boolean resolveUniqueItems(AnnotatedType a, io.swagger.v3.oas.annotations.media.ArraySchema arraySchema) {
    if(arraySchema != null) {
      if(arraySchema.uniqueItems()) {
        return arraySchema.uniqueItems();
      }
    }
    return null;
  }

  protected Map<String, Object> resolveExtensions(AnnotatedType a, io.swagger.v3.oas.annotations.media.ArraySchema arraySchema) {
    if(arraySchema != null &&
         arraySchema.extensions() != null &&
         arraySchema.extensions().length > 0) {
      return AnnotationsUtils.getExtensions(arraySchema.extensions());
    }
    return null;
  }

  protected void resolveSchemaMembers(Schema schema, AnnotatedType annotatedType) {
    final JavaType type;
    if(annotatedType.getType() instanceof JavaType) {
      type = (JavaType)annotatedType.getType();
    } else {
      type = _mapper.constructType(annotatedType.getType());
    }

    final Annotation resolvedSchemaOrArrayAnnotation = AnnotationsUtils.mergeSchemaAnnotations(annotatedType.getCtxAnnotations(), type);
    final io.swagger.v3.oas.annotations.media.Schema schemaAnnotation =
      resolvedSchemaOrArrayAnnotation == null ?
        null :
        resolvedSchemaOrArrayAnnotation instanceof io.swagger.v3.oas.annotations.media.ArraySchema ?
          ((io.swagger.v3.oas.annotations.media.ArraySchema)resolvedSchemaOrArrayAnnotation).schema() :
          (io.swagger.v3.oas.annotations.media.Schema)resolvedSchemaOrArrayAnnotation;

    final BeanDescription beanDesc = _mapper.getSerializationConfig().introspect(type);
    Annotated a = beanDesc.getClassInfo();
    Annotation[] annotations = annotatedType.getCtxAnnotations();
    resolveSchemaMembers(schema, a, annotations, schemaAnnotation);
  }

  protected void resolveSchemaMembers(Schema schema, Annotated a, Annotation[] annotations, io.swagger.v3.oas.annotations.media.Schema schemaAnnotation) {

    String description = resolveDescription(a, annotations, schemaAnnotation);
    if(StringUtils.isNotBlank(description)) {
      schema.description(description);
    }
    String title = resolveTitle(a, annotations, schemaAnnotation);
    if(StringUtils.isNotBlank(title)) {
      schema.title(title);
    }
    String format = resolveFormat(a, annotations, schemaAnnotation);
    if(StringUtils.isNotBlank(format) && StringUtils.isBlank(schema.getFormat())) {
      schema.format(format);
    }
    String defaultValue = resolveDefaultValue(a, annotations, schemaAnnotation);
    if(StringUtils.isNotBlank(defaultValue)) {
      schema.setDefault(defaultValue);
    }
    Object example = resolveExample(a, annotations, schemaAnnotation);
    if(example != null) {
      schema.example(example);
    }
    Boolean readOnly = resolveReadOnly(a, annotations, schemaAnnotation);
    if(readOnly != null) {
      schema.readOnly(readOnly);
    }
    Boolean nullable = resolveNullable(a, annotations, schemaAnnotation);
    if(nullable != null) {
      schema.nullable(nullable);
    }
    BigDecimal multipleOf = resolveMultipleOf(a, annotations, schemaAnnotation);
    if(multipleOf != null) {
      schema.multipleOf(multipleOf);
    }
    Integer maxLength = resolveMaxLength(a, annotations, schemaAnnotation);
    if(maxLength != null) {
      schema.maxLength(maxLength);
    }
    Integer minLength = resolveMinLength(a, annotations, schemaAnnotation);
    if(minLength != null) {
      schema.minLength(minLength);
    }
    BigDecimal minimum = resolveMinimum(a, annotations, schemaAnnotation);
    if(minimum != null) {
      schema.minimum(minimum);
    }
    BigDecimal maximum = resolveMaximum(a, annotations, schemaAnnotation);
    if(maximum != null) {
      schema.maximum(maximum);
    }
    Boolean exclusiveMinimum = resolveExclusiveMinimum(a, annotations, schemaAnnotation);
    if(exclusiveMinimum != null) {
      schema.exclusiveMinimum(exclusiveMinimum);
    }
    Boolean exclusiveMaximum = resolveExclusiveMaximum(a, annotations, schemaAnnotation);
    if(exclusiveMaximum != null) {
      schema.exclusiveMaximum(exclusiveMaximum);
    }
    String pattern = resolvePattern(a, annotations, schemaAnnotation);
    if(StringUtils.isNotBlank(pattern)) {
      schema.pattern(pattern);
    }
    Integer minProperties = resolveMinProperties(a, annotations, schemaAnnotation);
    if(minProperties != null) {
      schema.minProperties(minProperties);
    }
    Integer maxProperties = resolveMaxProperties(a, annotations, schemaAnnotation);
    if(maxProperties != null) {
      schema.maxProperties(maxProperties);
    }
    List<String> requiredProperties = resolveRequiredProperties(a, annotations, schemaAnnotation);
    if(requiredProperties != null) {
      for(String prop : requiredProperties) {
        addRequiredItem(schema, prop);
      }
    }
    Boolean writeOnly = resolveWriteOnly(a, annotations, schemaAnnotation);
    if(writeOnly != null) {
      schema.writeOnly(writeOnly);
    }
    ExternalDocumentation externalDocs = resolveExternalDocumentation(a, annotations, schemaAnnotation);
    if(externalDocs != null) {
      schema.externalDocs(externalDocs);
    }
    Boolean deprecated = resolveDeprecated(a, annotations, schemaAnnotation);
    if(deprecated != null) {
      schema.deprecated(deprecated);
    }
    List<String> allowableValues = resolveAllowableValues(a, annotations, schemaAnnotation);
    if(allowableValues != null) {
      for(String prop : allowableValues) {
        schema.addEnumItemObject(prop);
      }
    }

    Map<String, Object> extensions = resolveExtensions(a, annotations, schemaAnnotation);
    if(extensions != null) {
      for(String ext : extensions.keySet()) {
        schema.addExtension(ext, extensions.get(ext));
      }
    }
  }

  protected void addRequiredItem(Schema model, String propName) {
    if(model == null || propName == null || StringUtils.isBlank(propName)) {
      return;
    }
    if(model.getRequired() == null || model.getRequired().isEmpty()) {
      model.addRequiredItem(propName);
    }
    if(model.getRequired().stream().noneMatch(s -> propName.equals(s))) {
      model.addRequiredItem(propName);
    }
  }

  protected boolean shouldIgnoreClass(Type type) {
    if(type instanceof Class) {
      Class<?> cls = (Class<?>)type;
      if(cls.getName().equals("javax.ws.rs.Response")) {
        return true;
      }
    } else {
      if(type instanceof com.fasterxml.jackson.core.type.ResolvedType) {
        com.fasterxml.jackson.core.type.ResolvedType rt = (com.fasterxml.jackson.core.type.ResolvedType)type;
        // LOGGER.trace("Can't check class {}, {}", type, rt.getRawClass().getName());
        if(rt.getRawClass().equals(Class.class)) {
          return true;
        }
      }
    }
    return false;
  }

  private List<String> getIgnoredProperties(BeanDescription beanDescription) {
    AnnotationIntrospector introspector = _mapper.getSerializationConfig().getAnnotationIntrospector();
    String[] ignored = introspector.findPropertiesToIgnore(beanDescription.getClassInfo(), true);
    return ignored == null ? Collections.emptyList() : Arrays.asList(ignored);
  }

  /**
   * Decorate the name based on the JsonView
   */
  protected String decorateModelName(AnnotatedType type, String originalName) {
    if(StringUtils.isBlank(originalName)) {
      return originalName;
    }
    String name = originalName;
    if(type.getJsonViewAnnotation() != null && type.getJsonViewAnnotation().value().length > 0) {
      String COMBINER = "-or-";
      StringBuilder sb = new StringBuilder();
      for(Class<?> view : type.getJsonViewAnnotation().value()) {
        sb.append(view.getSimpleName()).append(COMBINER);
      }
      String suffix = sb.substring(0, sb.length() - COMBINER.length());
      name = originalName + "_" + suffix;
    }
    return name;
  }

  private boolean hiddenByJsonView(Annotation[] annotations,
    AnnotatedType type) {
    JsonView jsonView = type.getJsonViewAnnotation();
    if(jsonView == null)
      return false;

    Class<?>[] filters = jsonView.value();
    boolean containsJsonViewAnnotation = false;
    for(Annotation ant : annotations) {
      if(ant instanceof JsonView) {
        containsJsonViewAnnotation = true;
        Class<?>[] views = ((JsonView)ant).value();
        for(Class<?> f : filters) {
          for(Class<?> v : views) {
            if(v == f || v.isAssignableFrom(f)) {
              return false;
            }
          }
        }
      }
    }
    return containsJsonViewAnnotation;
  }

  private void resolveArraySchema(AnnotatedType annotatedType, ArraySchema schema, io.swagger.v3.oas.annotations.media.ArraySchema resolvedArrayAnnotation) {
    Integer minItems = resolveMinItems(annotatedType, resolvedArrayAnnotation);
    if(minItems != null) {
      schema.minItems(minItems);
    }
    Integer maxItems = resolveMaxItems(annotatedType, resolvedArrayAnnotation);
    if(maxItems != null) {
      schema.maxItems(maxItems);
    }
    Boolean uniqueItems = resolveUniqueItems(annotatedType, resolvedArrayAnnotation);
    if(uniqueItems != null) {
      schema.uniqueItems(uniqueItems);
    }
    Map<String, Object> extensions = resolveExtensions(annotatedType, resolvedArrayAnnotation);
    if(extensions != null) {
      schema.extensions(extensions);
    }
    if(resolvedArrayAnnotation != null) {
      if(AnnotationsUtils.hasSchemaAnnotation(resolvedArrayAnnotation.arraySchema())) {
        resolveSchemaMembers(schema, null, null, resolvedArrayAnnotation.arraySchema());
      }
    }
  }
}
