# ConfigFile as API

The ConfigFile as API is a formal Treble interface which is schema of
configuration files used across system and vendor partitions.
So the Java APIs in current.txt file is not Java APIs for app. It's a interface
between system and vendor partition.

## Add Schema
When you add a schema, you don’t need to modify the parser code if you use the
generated code. If not, you should modify the parser code. First, add the schema
(attribute, element or new complexType …) which you want to add to the xsd file,
that is a src in xsd_config.

#### before
```xml
<xs:element name="class">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="student" type="xs:string"/>
    </xs:sequence>
    <xs:attribute name="name" type=”xs:string”/>
  </xs:complexType>
</xs:element>
```

#### after
```xml
<xs:element name="class">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="student" type="xs:string"/>
    </xs:sequence>
    <xs:attribute name="name" type=”xs:string”/>
    <xs:attribute name="number" type="xs:int"/>
  </xs:complexType>
</xs:element>
```

We have to update api or schema files after adding schema, and we can do this
through the "make {xsd_config module_name} .docs-update-current-api" command
(make update-api: To update all the xsd_config api or schema). In the above
example, two functions are added as below.
* method public int getNumber();
* method public void setNumber(int);

## Remove Schema
To remove a schema, we must inform it to partner, since we must guarantee that
the old version of the vendor image will work with the latest system image for 2
years. To test it on Pixel devices, we are running the mixed builds where old
vendor.img are combined with newest system.img (e.g. Q system + P vendor). We
are also planning to run CTS on the builds (go/flash-mixed).
To remove a tag, add an annotation tag with the name of "Deprecated" into the
tag to be deleted, and wait for the partners not to use the tag for a certain
period of time.

#### before
```xml
<xs:element name="class">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="student" type="xs:string"/>
    </xs:sequence>
    <xs:attribute name="name" type=”xs:string”/>
  </xs:complexType>
</xs:element>
```

#### after
```xml
<xs:element name="class">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="student" type="xs:string">
        <annotation name=”Deprecated”/>
      </xs:element>
    </xs:sequence>
    <xs:attribute name="name" type=”xs:string”/>
  </xs:complexType>
</xs:element>
```

After adding “Deprecated” annotation, we need to update the api or schema just
like when adding a tag. In the above example, @Deprecate annotation is added as
below.
* method @Deprecated public java.util.List<java.lang.String> getStudent();
After the period of time, we can delete the tag. When deleting, delete the tag
in xsd file and the api or schema in last_current.txt, and update it.

## Release Schema
If there are any changes, we should update last_current.txt and last_removed.txt
before release. The update method is to copy current.txt and removed.txt to
last_current.txt and last_removed.txt.

## Supported/Unsupported Tags
There are some unsupported .xsd tags for implementation simplicity. Unsupported
tags are simply ignored and skipped. In the following table, I wrote multiple
tags in one line for similar functions. Unimportant tags, which I think that
it’s not a core part, are put under the dashed line.

#### Supported
```xml
<choice>
<schema>
<element>, <attribute>, <complexType>
<simpleType>, <complexContent>
<simpleContent>
<restriction> (complexContent, simpleContent, simpleType)
<extension> (complexContent, simpleContent)
<list>, <union>, <sequence>
<selector>, <key>, <keyref>, <unique>, <field>
<annotation>, <documentation>
```

#### Unsupported
```xml
<all>,
<any>, <anyAttribute>
<attributeGroup>, <group>
<redefine>
<import>, <include>
------------------------------
<appinfo>
<notation>
```

### Other Unsupported Features
We do not support all the attributes of supported tags, for simplicity. Below I
listed some features I did not implement.
* default, fixed value of an element/attribute
* substitutionGroup of an element
* abstract option of an element/complexType
* minOccurs, maxOccurs option of a sequence (that of an element is supported)
* Pattern, whiteSpace, length, minLength, maxLength, minInclusive and maxInclusive option of a restriction (just check the type)
