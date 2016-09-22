/*
 * decider.h
 *
 *  Created on: Sep 11, 2016
 *      Author: mazzin
 */

#ifndef CORE_SOARKERNEL_SRC_DECISION_PROCESS_DECIDER_H_
#define CORE_SOARKERNEL_SRC_DECISION_PROCESS_DECIDER_H_

#include "kernel.h"
#include "decider_settings.h"

class SoarDecider
{
        friend cli::CommandLineInterface;


    public:

        /* General methods */

        SoarDecider(agent* myAgent);
        ~SoarDecider() {};

        void clean_up_for_agent_deletion();

        /* Settings and cli command related functions */
        decider_param_container*    params;
        uint64_t                    settings[num_decider_settings];

        void                        print_soar_status(sml::KernelSML* pKernelSML);

    private:

        agent*                      thisAgent;
        Output_Manager*             outputManager;

};

#endif /* CORE_SOARKERNEL_SRC_DECISION_PROCESS_DECIDER_H_ */