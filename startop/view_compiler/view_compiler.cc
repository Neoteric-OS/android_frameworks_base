/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "view_compiler.h"

namespace startop {

void LayoutValidationVisitor::VisitStartTag(const std::u16string& name) {
  if (0 == name.compare(u"merge")) {
    message_ = "Merge tags are not supported";
    can_compile_ = false;
  }
  if (0 == name.compare(u"include")) {
    message_ = "Include tags are not supported";
    can_compile_ = false;
  }
}

DexViewBuilder::DexViewBuilder(dex::MethodBuilder* method)
    : method_{method},
      context_{dex::Value::Parameter(0)},
      resid_{dex::Value::Parameter(1)},
      inflater_{method->MakeRegister()},
      xml_{method->MakeRegister()},
      attrs_{method->MakeRegister()},
      classname_tmp_{method->MakeRegister()},
      layout_params_tmp_{method->MakeRegister()},
      xml_next_{method->dex_file()->GetOrDeclareMethod(
          dex::TypeDescriptor::FromClassname("android.content.res.XmlResourceParser"), "next",
          dex::Prototype{dex::TypeDescriptor::Int()})},
      try_create_view_group_{method->dex_file()->GetOrDeclareMethod(
          dex::TypeDescriptor::FromClassname("android.view.LayoutInflater"), "tryCreateViewGroup",
          dex::Prototype{dex::TypeDescriptor::FromClassname("android.view.ViewGroup"),
                         dex::TypeDescriptor::FromClassname("android.view.View"),
                         dex::TypeDescriptor::FromClassname("java.lang.String"),
                         dex::TypeDescriptor::FromClassname("android.content.Context"),
                         dex::TypeDescriptor::FromClassname("android.util.AttributeSet")})},
      try_create_view_{method->dex_file()->GetOrDeclareMethod(
          dex::TypeDescriptor::FromClassname("android.view.LayoutInflater"), "tryCreateView",
          dex::Prototype{dex::TypeDescriptor::FromClassname("android.view.View"),
                         dex::TypeDescriptor::FromClassname("android.view.View"),
                         dex::TypeDescriptor::FromClassname("java.lang.String"),
                         dex::TypeDescriptor::FromClassname("android.content.Context"),
                         dex::TypeDescriptor::FromClassname("android.util.AttributeSet")})},
      generate_layout_params_{method->dex_file()->GetOrDeclareMethod(
          dex::TypeDescriptor::FromClassname("android.view.ViewGroup"), "generateLayoutParams",
          dex::Prototype{dex::TypeDescriptor::FromClassname("android.view.ViewGroup$LayoutParams"),
                         dex::TypeDescriptor::FromClassname("android.util.AttributeSet")})},
      add_view_{method->dex_file()->GetOrDeclareMethod(
          dex::TypeDescriptor::FromClassname("android.view.ViewGroup"), "addView",
          dex::Prototype{
              dex::TypeDescriptor::Void(),
              dex::TypeDescriptor::FromClassname("android.view.View"),
              dex::TypeDescriptor::FromClassname("android.view.ViewGroup$LayoutParams")})},
      // The register stack starts with one register, which will be null for the root view.
      register_stack_{{method->MakeRegister()}} {}

void DexViewBuilder::Start() {
  dex::DexBuilder* const dex = method_->dex_file();

  // zero out the top of the stack
  // TODO: accept a parent view from the inflater
  method_->BuildConst4(GetCurrentView(), 0);

  // LayoutInflater inflater = LayoutInflater.from(context);
  auto layout_inflater_from = dex->GetOrDeclareMethod(
      dex::TypeDescriptor::FromClassname("android.view.LayoutInflater"),
      "from",
      dex::Prototype{dex::TypeDescriptor::FromClassname("android.view.LayoutInflater"),
                     dex::TypeDescriptor::FromClassname("android.content.Context")});
  method_->AddInstruction(
      dex::Instruction::InvokeStaticObject(layout_inflater_from.id, /*dest=*/inflater_, context_));

  // Resources res = context.getResources();
  auto context_type = dex::TypeDescriptor::FromClassname("android.content.Context");
  auto resources_type = dex::TypeDescriptor::FromClassname("android.content.res.Resources");
  auto get_resources =
      dex->GetOrDeclareMethod(context_type, "getResources", dex::Prototype{resources_type});
  method_->AddInstruction(dex::Instruction::InvokeVirtualObject(get_resources.id, xml_, context_));

  // XmlResourceParser xml = res.getLayout(resid);
  auto xml_resource_parser_type =
      dex::TypeDescriptor::FromClassname("android.content.res.XmlResourceParser");
  auto get_layout =
      dex->GetOrDeclareMethod(resources_type,
                              "getLayout",
                              dex::Prototype{xml_resource_parser_type, dex::TypeDescriptor::Int()});
  method_->AddInstruction(dex::Instruction::InvokeVirtualObject(get_layout.id, xml_, xml_, resid_));

  // AttributeSet attrs = Xml.asAttributeSet(xml);
  auto as_attribute_set = dex->GetOrDeclareMethod(
      dex::TypeDescriptor::FromClassname("android.util.Xml"),
      "asAttributeSet",
      dex::Prototype{dex::TypeDescriptor::FromClassname("android.util.AttributeSet"),
                     dex::TypeDescriptor::FromClassname("org.xmlpull.v1.XmlPullParser")});
  method_->AddInstruction(dex::Instruction::InvokeStaticObject(as_attribute_set.id, attrs_, xml_));

  // xml.next(); // start document
  method_->AddInstruction(dex::Instruction::InvokeInterface(xml_next_.id, {}, xml_));
}

void DexViewBuilder::Finish() {}

void DexViewBuilder::StartView(const std::string& name, bool is_viewgroup) {
  LOG(INFO) << "starting view " << name << " " << is_viewgroup;

  // xml.next(); // start tag
  method_->AddInstruction(dex::Instruction::InvokeInterface(xml_next_.id, {}, xml_));

  size_t try_create = (is_viewgroup ? try_create_view_group_ : try_create_view_).id;
  dex::Value view = AcquireRegister();
  // try to create the view using the factories
  method_->BuildConstString(classname_tmp_,
                            name);  // TODO: the need to fully qualify the classname
  method_->AddInstruction(dex::Instruction::InvokeVirtualObject(
      try_create, view, inflater_, GetParentView(), classname_tmp_, context_, attrs_));
  auto label = method_->MakeLabel();

  // branch if not null
  method_->AddInstruction(
      dex::Instruction::OpWithArgs(dex::Instruction::Op::kBranchNEqz, /*dest=*/{}, view, label));

  // If null, create the class directly.
  method_->BuildNew(view,
                    dex::TypeDescriptor::FromClassname(name),
                    dex::Prototype{dex::TypeDescriptor::Void(),
                                   dex::TypeDescriptor::FromClassname("android.content.Context"),
                                   dex::TypeDescriptor::FromClassname("android.util.AttributeSet")},
                    context_,
                    attrs_);

  method_->AddInstruction(
      dex::Instruction::OpWithArgs(dex::Instruction::Op::kBindLabel, /*dest=*/{}, label));
}

void DexViewBuilder::FinishView() {
  auto view = ReleaseRegister();
  if (IsRootView()) {
    method_->BuildReturn(view, /*is_object=*/true);
  } else {
    // layout_params = parent.generateLayoutParams(attrs);
    method_->AddInstruction(dex::Instruction::InvokeVirtualObject(
        generate_layout_params_.id, layout_params_tmp_, GetCurrentView(), attrs_));
    // parent.add(view)
    method_->AddInstruction(dex::Instruction::InvokeVirtual(
        add_view_.id, /*dest=*/{}, GetCurrentView(), view, layout_params_tmp_));
    // xml.next(); // end tag
    method_->AddInstruction(dex::Instruction::InvokeInterface(xml_next_.id, {}, xml_));
  }
}

dex::Value DexViewBuilder::AcquireRegister() {
  top_register_++;
  if (register_stack_.size() == top_register_) {
    register_stack_.push_back(method_->MakeRegister());
  }
  return register_stack_[top_register_];
}

dex::Value DexViewBuilder::ReleaseRegister() {
  dex::Value reg = register_stack_[top_register_];
  top_register_--;
  return reg;
}

dex::Value DexViewBuilder::GetCurrentView() const { return register_stack_[top_register_]; }
dex::Value DexViewBuilder::GetParentView() const { return register_stack_[top_register_ - 1]; }

bool DexViewBuilder::IsRootView() const { return top_register_ == 0; }

}  // namespace startop