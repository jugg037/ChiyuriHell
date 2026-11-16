#if UNITY_EDITOR
using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using UnityEditor;
using UnityEditor.Animations;
using UnityEngine;

namespace ChiyuriAI.EditorTools
{
    [Serializable]
    public sealed class ChiyuriAvatarInventory
    {
        public int schemaVersion = 1;
        public string avatarName;
        public string unityVersion;
        public string generatedUtc;
        public List<string> componentTypes = new List<string>();
        public List<string> toggleCandidates = new List<string>();
        public List<ChiyuriAnimatorInventory> animators = new List<ChiyuriAnimatorInventory>();
        public List<string> warnings = new List<string>();
    }

    [Serializable]
    public sealed class ChiyuriAnimatorInventory
    {
        public string hierarchyPath;
        public string controllerName;
        public List<ChiyuriAnimatorParameter> parameters = new List<ChiyuriAnimatorParameter>();
        public List<ChiyuriAnimatorLayer> layers = new List<ChiyuriAnimatorLayer>();
    }

    [Serializable]
    public sealed class ChiyuriAnimatorParameter
    {
        public string name;
        public string type;
        public float defaultFloat;
        public int defaultInt;
        public bool defaultBool;
    }

    [Serializable]
    public sealed class ChiyuriAnimatorLayer
    {
        public string name;
        public float defaultWeight;
        public List<ChiyuriAnimatorState> states = new List<ChiyuriAnimatorState>();
    }

    [Serializable]
    public sealed class ChiyuriAnimatorState
    {
        public string name;
        public string fullPath;
        public string motionName;
        public string motionType;
        public List<ChiyuriBlendChild> blendChildren = new List<ChiyuriBlendChild>();
        public List<ChiyuriTransition> transitions = new List<ChiyuriTransition>();
    }

    [Serializable]
    public sealed class ChiyuriBlendChild
    {
        public string motionName;
        public float threshold;
        public float timeScale;
    }

    [Serializable]
    public sealed class ChiyuriTransition
    {
        public string destination;
        public bool hasExitTime;
        public float exitTime;
        public float duration;
        public List<string> conditions = new List<string>();
    }

    public static class ChiyuriAvatarInspector
    {
        private static readonly Regex ToggleName = new Regex(
            "(toggle|enable|active|show|hide|visible|onoff)",
            RegexOptions.IgnoreCase | RegexOptions.Compiled
        );

        [MenuItem("Chiyuri/Avatar/Inspect Selected Avatar (Read Only)")]
        public static void InspectSelectedAvatar()
        {
            GameObject root = Selection.activeGameObject;
            if (root == null)
            {
                EditorUtility.DisplayDialog(
                    "Chiyuri Avatar Inspector",
                    "Select the avatar root GameObject in the Hierarchy first.",
                    "OK"
                );
                return;
            }

            ChiyuriAvatarInventory inventory = Inspect(root);
            string initialDirectory = Directory.GetParent(Application.dataPath).FullName;
            string outputPath = EditorUtility.SaveFilePanel(
                "Export Chiyuri avatar inventory",
                initialDirectory,
                root.name + "_ChiyuriAvatarInventory",
                "json"
            );
            if (string.IsNullOrEmpty(outputPath))
                return;

            try
            {
                string json = JsonUtility.ToJson(inventory, true);
                File.WriteAllText(outputPath, json, new UTF8Encoding(false));
                EditorUtility.DisplayDialog(
                    "Chiyuri Avatar Inspector",
                    "Read-only inventory exported. No avatar assets were changed.\n\n" + outputPath,
                    "OK"
                );
            }
            catch (Exception exception)
            {
                Debug.LogException(exception);
                EditorUtility.DisplayDialog(
                    "Chiyuri Avatar Inspector",
                    "Export failed: " + exception.Message,
                    "OK"
                );
            }
        }

        public static ChiyuriAvatarInventory Inspect(GameObject root)
        {
            if (root == null)
                throw new ArgumentNullException("root");

            ChiyuriAvatarInventory inventory = new ChiyuriAvatarInventory();
            inventory.avatarName = root.name;
            inventory.unityVersion = Application.unityVersion;
            inventory.generatedUtc = DateTime.UtcNow.ToString("o");

            Component[] components = root.GetComponentsInChildren<Component>(true);
            HashSet<string> componentTypes = new HashSet<string>();
            for (int i = 0; i < components.Length; i++)
            {
                Component component = components[i];
                if (component == null)
                {
                    inventory.warnings.Add("A missing-script component was found.");
                    continue;
                }

                string typeName = component.GetType().FullName;
                if (!string.IsNullOrEmpty(typeName))
                    componentTypes.Add(typeName);
            }

            inventory.componentTypes.AddRange(componentTypes);
            inventory.componentTypes.Sort(StringComparer.OrdinalIgnoreCase);

            Animator[] animators = root.GetComponentsInChildren<Animator>(true);
            for (int i = 0; i < animators.Length; i++)
            {
                Animator animator = animators[i];
                if (animator == null)
                    continue;

                ChiyuriAnimatorInventory animatorInfo = InspectAnimator(root, animator, inventory);
                inventory.animators.Add(animatorInfo);
            }

            inventory.toggleCandidates.Sort(StringComparer.OrdinalIgnoreCase);
            return inventory;
        }

        private static ChiyuriAnimatorInventory InspectAnimator(
            GameObject root,
            Animator animator,
            ChiyuriAvatarInventory inventory
        )
        {
            ChiyuriAnimatorInventory result = new ChiyuriAnimatorInventory();
            result.hierarchyPath = GetHierarchyPath(root.transform, animator.transform);

            RuntimeAnimatorController runtimeController = animator.runtimeAnimatorController;
            while (runtimeController is AnimatorOverrideController)
            {
                runtimeController = ((AnimatorOverrideController)runtimeController).runtimeAnimatorController;
            }

            if (runtimeController == null)
            {
                inventory.warnings.Add("Animator has no controller: " + result.hierarchyPath);
                return result;
            }

            result.controllerName = runtimeController.name;
            AnimatorController controller = runtimeController as AnimatorController;
            if (controller == null)
            {
                inventory.warnings.Add(
                    "Controller is not an editable AnimatorController: " + result.controllerName
                );
                return result;
            }

            AnimatorControllerParameter[] parameters = controller.parameters;
            for (int i = 0; i < parameters.Length; i++)
            {
                AnimatorControllerParameter parameter = parameters[i];
                ChiyuriAnimatorParameter parameterInfo = new ChiyuriAnimatorParameter();
                parameterInfo.name = parameter.name;
                parameterInfo.type = parameter.type.ToString();
                parameterInfo.defaultFloat = parameter.defaultFloat;
                parameterInfo.defaultInt = parameter.defaultInt;
                parameterInfo.defaultBool = parameter.defaultBool;
                result.parameters.Add(parameterInfo);

                if (ToggleName.IsMatch(parameter.name) && !inventory.toggleCandidates.Contains(parameter.name))
                    inventory.toggleCandidates.Add(parameter.name);
            }

            AnimatorControllerLayer[] layers = controller.layers;
            for (int i = 0; i < layers.Length; i++)
            {
                AnimatorControllerLayer layer = layers[i];
                ChiyuriAnimatorLayer layerInfo = new ChiyuriAnimatorLayer();
                layerInfo.name = layer.name;
                layerInfo.defaultWeight = layer.defaultWeight;
                if (layer.stateMachine != null)
                {
                    CollectStates(
                        layer.stateMachine,
                        layer.name,
                        layerInfo.states,
                        inventory,
                        new HashSet<int>()
                    );
                }
                result.layers.Add(layerInfo);
            }

            return result;
        }

        private static void CollectStates(
            AnimatorStateMachine stateMachine,
            string parentPath,
            List<ChiyuriAnimatorState> output,
            ChiyuriAvatarInventory inventory,
            HashSet<int> visited
        )
        {
            if (stateMachine == null || !visited.Add(stateMachine.GetInstanceID()))
                return;

            ChildAnimatorState[] states = stateMachine.states;
            for (int i = 0; i < states.Length; i++)
            {
                AnimatorState state = states[i].state;
                if (state == null)
                    continue;

                ChiyuriAnimatorState stateInfo = new ChiyuriAnimatorState();
                stateInfo.name = state.name;
                stateInfo.fullPath = parentPath + "/" + state.name;
                if (state.motion != null)
                {
                    stateInfo.motionName = state.motion.name;
                    stateInfo.motionType = state.motion.GetType().Name;
                    CollectBlendChildren(state.motion, stateInfo.blendChildren, new HashSet<int>());
                }

                AnimatorStateTransition[] transitions = state.transitions;
                for (int j = 0; j < transitions.Length; j++)
                {
                    AnimatorStateTransition transition = transitions[j];
                    ChiyuriTransition transitionInfo = new ChiyuriTransition();
                    transitionInfo.destination = transition.destinationState != null
                        ? transition.destinationState.name
                        : (transition.destinationStateMachine != null
                            ? transition.destinationStateMachine.name
                            : "Exit");
                    transitionInfo.hasExitTime = transition.hasExitTime;
                    transitionInfo.exitTime = transition.exitTime;
                    transitionInfo.duration = transition.duration;

                    AnimatorCondition[] conditions = transition.conditions;
                    for (int k = 0; k < conditions.Length; k++)
                    {
                        AnimatorCondition condition = conditions[k];
                        transitionInfo.conditions.Add(
                            condition.parameter + " " + condition.mode + " " + condition.threshold
                        );
                    }
                    stateInfo.transitions.Add(transitionInfo);
                }

                output.Add(stateInfo);
                if (ToggleName.IsMatch(state.name) && !inventory.toggleCandidates.Contains(state.name))
                    inventory.toggleCandidates.Add(state.name);
            }

            ChildAnimatorStateMachine[] childMachines = stateMachine.stateMachines;
            for (int i = 0; i < childMachines.Length; i++)
            {
                AnimatorStateMachine child = childMachines[i].stateMachine;
                if (child != null)
                    CollectStates(child, parentPath + "/" + child.name, output, inventory, visited);
            }
        }

        private static void CollectBlendChildren(
            Motion motion,
            List<ChiyuriBlendChild> output,
            HashSet<int> visited
        )
        {
            BlendTree blendTree = motion as BlendTree;
            if (blendTree == null || !visited.Add(blendTree.GetInstanceID()))
                return;

            ChildMotion[] children = blendTree.children;
            for (int i = 0; i < children.Length; i++)
            {
                ChildMotion child = children[i];
                ChiyuriBlendChild childInfo = new ChiyuriBlendChild();
                childInfo.motionName = child.motion != null ? child.motion.name : "(missing)";
                childInfo.threshold = child.threshold;
                childInfo.timeScale = child.timeScale;
                output.Add(childInfo);
                if (child.motion != null)
                    CollectBlendChildren(child.motion, output, visited);
            }
        }

        private static string GetHierarchyPath(Transform root, Transform target)
        {
            if (target == root)
                return root.name;

            List<string> segments = new List<string>();
            Transform current = target;
            while (current != null && current != root)
            {
                segments.Add(current.name);
                current = current.parent;
            }
            segments.Reverse();
            return root.name + "/" + string.Join("/", segments.ToArray());
        }
    }
}
#endif
