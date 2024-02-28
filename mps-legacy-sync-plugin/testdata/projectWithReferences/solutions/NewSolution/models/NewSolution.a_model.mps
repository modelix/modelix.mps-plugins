<?xml version="1.0" encoding="UTF-8"?>
<model ref="r:7834ce38-0c6e-4f10-8390-d6c40765da4f(NewSolution.a_model)">
  <persistence version="9" />
  <languages>
    <use id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage" version="11" />
  </languages>
  <imports />
  <registry>
    <language id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage">
      <concept id="1070462154015" name="jetbrains.mps.baseLanguage.structure.StaticFieldDeclaration" flags="ig" index="Wx3nA" />
      <concept id="1068390468198" name="jetbrains.mps.baseLanguage.structure.ClassConcept" flags="ig" index="312cEu" />
      <concept id="4972933694980447171" name="jetbrains.mps.baseLanguage.structure.BaseVariableDeclaration" flags="ng" index="19Szcq">
        <child id="5680397130376446158" name="type" index="1tU5fm" />
      </concept>
      <concept id="1107461130800" name="jetbrains.mps.baseLanguage.structure.Classifier" flags="ng" index="3pOWGL">
        <child id="5375687026011219971" name="member" index="jymVt" unordered="true" />
      </concept>
      <concept id="1107535904670" name="jetbrains.mps.baseLanguage.structure.ClassifierType" flags="in" index="3uibUv">
        <reference id="1107535924139" name="classifier" index="3uigEE" />
      </concept>
      <concept id="1178549954367" name="jetbrains.mps.baseLanguage.structure.IVisible" flags="ng" index="1B3ioH">
        <child id="1178549979242" name="visibility" index="1B3o_S" />
      </concept>
      <concept id="1146644602865" name="jetbrains.mps.baseLanguage.structure.PublicVisibility" flags="nn" index="3Tm1VV" />
    </language>
    <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
      <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ng" index="TrEIO">
        <property id="1169194664001" name="name" index="TrG5h" />
      </concept>
    </language>
  </registry>
  <node concept="312cEu" id="1b97tCqtR4X">
    <property role="TrG5h" value="ClassA" />
    <node concept="Wx3nA" id="1b97tCqtU9w" role="jymVt">
      <property role="TrG5h" value="classBreference" />
      <node concept="3uibUv" id="1b97tCqtU9x" role="1tU5fm">
        <ref role="3uigEE" node="1b97tCqtU7Y" resolve="ClassB" />
      </node>
    </node>
    <node concept="3Tm1VV" id="1b97tCqtR4Y" role="1B3o_S" />
  </node>
  <node concept="312cEu" id="1b97tCqtU7Y">
    <property role="TrG5h" value="ClassB" />
    <node concept="Wx3nA" id="1b97tCqtU91" role="jymVt">
      <property role="TrG5h" value="classAreference" />
      <node concept="3uibUv" id="1b97tCqtU8Q" role="1tU5fm">
        <ref role="3uigEE" node="1b97tCqtR4X" resolve="ClassA" />
      </node>
    </node>
    <node concept="3Tm1VV" id="1b97tCqtU7Z" role="1B3o_S" />
  </node>
</model>
